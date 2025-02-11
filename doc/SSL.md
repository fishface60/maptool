# Securing MapTool connections with SSL

## 0. A brief explainer of the data model

SSL requires the server to provide a certificate
signed by a Certification Authority that is trusted by the client.

A GM with expirence administering web services could sort this out themselves
but even GMs with this experience will find this to be a barrier to entry
that discourages securing MapTool servers with SSL so we're going to assume
the average GM wants MapTool to manage this itself.

Since MapTool isn't like a web server, we generally don't use domain names
and the certificates have a validity period much longer than the average game
we can't request a Let's Encrypt certificate
so we have to rely on self-signed certificates.

Because certificates must include the addresses they're valid for
and MapTool must support dynamic IP address allocation
we must issue a new certificate every session.

Issuing a new self-signed certificate every session would require out-of-band
verification every session which is going to become tiresome.

The solution is that every MapTool server works as a Certification Authority
and this CA's root certificate can be verified once
and the server can issue a new certificate signed by the CA root certificate
for every session.

Since MapTool already includes RSA keys for client authentication
we can build on top of this to make a CA for server security.

## 1. Creating a Certification Authority

**NOTE**: These manual steps are intended to be automated later.

First, define some directories to organise things and some CA-specific files.

```
mkdir -p ~/.maptool-rptools/config/ca/{certs,crl,newcerts,private,csr}
echo 1000 > ~/.maptool-rptools/config/ca/serial
echo 0100 > ~/.maptool-rptools/config/ca/crlnumber
touch ~/.maptool-rptools/config/ca/index.txt
```

The private key should have its access restricted.

<!-- Best practice has an intermediate CA and the root CA's keys
are kept in hardware tokens such as a TPM or offline in removable storage
but this is for guarding against the possibility of the intermediate CA's keys
being compromised so the root CA can issue a revocation.
However this assumes central distribution of certificates and an update process
that can distribute revocations, which would require rptools to act as a CA
and for MapTool to phone home for CRL updates.
The added complexity of setting up rptools to act as a CA and issue revocations
and provide trustable interfaces for communicating that the intermediate CA
has been compromised does not seem worth the effort compared with the GM asking
their players to remove the CA certificate.
-->

```
chmod 400 ~/.maptool-rptools/config/private.key
```

Then, we must generate the root certificate

```
openssl req -new -x509 -days 3650 -key ~/.maptool-rptools/config/private.key -out ~/.maptool-rptools/config/ca/certs/ca.crt -subj "/CN=MapTool $(cat ~/.maptool-rptools/client-id) CA Root"
```

`-subj "/CN=..."` is a unique identifier for the certificate.
It doesn't need to be globally unique but it must not be duplicated
within a certificate validation chain.
Using the MapTool client-id and giving it an appropriate prefix and suffix
should ensure sufficient uniqueness.

<!-- TODO: How do root cert updates work? What do we do in 10 years? -->

<!-- TODO: Is CA config needed? We don't currently pass it to any commands.
cat >~/.maptool-rptools/config/ca/ca.cnf <<EOF
[ ca ]                                                   # The default CA section
default_ca = CA_default                                  # The default CA name

[ CA_default ]                                           # Default settings for the CA
dir               = $HOME/.maptool-rptools/config        # CA directory
certs             = \$dir/ca/certs                       # Certificates directory
crl_dir           = \$dir/ca/crl                         # CRL directory
new_certs_dir     = \$dir/ca/newcerts                    # New certificates directory
database          = \$dir/ca/index.txt                   # Certificate index file
serial            = \$dir/ca/serial                      # Serial number file
RANDFILE          = \$dir/ca/private/.rand               # Random number file
private_key       = \$dir/private.key                    # Root CA private key
certificate       = \$dir/ca/certs/ca.crt                # Root CA certificate
crl               = \$dir/ca/crl/ca.crl.pem              # Root CA CRL
crlnumber         = \$dir/ca/crlnumber                   # Root CA CRL number
crl_extensions    = crl_ext                              # CRL extensions
default_crl_days  = 30                                   # Default CRL validity days
default_md        = sha256                               # Default message digest
preserve          = no                                   # Preserve existing extensions
email_in_dn       = no                                   # Exclude email from the DN
name_opt          = ca_default                           # Formatting options for names
cert_opt          = ca_default                           # Certificate output options
policy            = policy_strict                        # Certificate policy
unique_subject    = no                                   # Allow multiple certs with the same DN

[ policy_strict ]                                        # Policy for stricter validation
countryName             = optional                       # Must match the issuer's country
stateOrProvinceName     = optional                       # Must match the issuer's state
organizationName        = optional                       # Must match the issuer's organization
organizationalUnitName  = optional                       # Organizational unit is optional
commonName              = supplied                       # Must provide a common name
emailAddress            = optional                       # Email address is optional

[ req ]                                                  # Request settings
default_bits        = 2048                               # Default key size
distinguished_name  = req_distinguished_name             # Default DN template
string_mask         = utf8only                           # UTF-8 encoding
default_md          = sha256                             # Default message digest
prompt              = no                                 # Non-interactive mode

[ req_distinguished_name ]                               # Template for the DN in the CSR
commonName                      = Common Name (your domain)

[ v3_ca ]                                           # Root CA certificate extensions
subjectKeyIdentifier = hash                         # Subject key identifier
authorityKeyIdentifier = keyid:always,issuer        # Authority key identifier
basicConstraints = critical, CA:true                # Basic constraints for a CA
keyUsage = critical, keyCertSign, cRLSign           # Key usage for a CA

[ crl_ext ]                                         # CRL extensions
authorityKeyIdentifier = keyid:always,issuer        # Authority key identifier

[ v3_intermediate_ca ]
subjectKeyIdentifier = hash
authorityKeyIdentifier = keyid:always,issuer
basicConstraints = critical, CA:true, pathlen:0
keyUsage = critical, digitalSignature, cRLSign, keyCertSign
EOF
-->

## 2. Creating a new certificate

**NOTE**: These manual steps are intended to be automated later.

First we must determine the set of addresses the certificate will be valid for.
This shell snippet collects it using `miniupnpc`'s `external-ip` and parsing `ip`.

```
ips=()
localips=()
subjectAltName=()
if extip="$(external-ip)"; then
  ips+=("$extip")
  subjectAltName+=("IP:$extip")
fi
for localaddr in $(ip -json addr show up | jq -r '.[].addr_info[]|select(.scope == "global").local'); do
  ips+=("$localaddr")
  localips+=("$localaddr")
  subjectAltName+=("IP:$localaddr")
done
```

Now create a Certificate Signing Request.
This is convoluted because the entity requesting a certificate isn't usually
the same as the CA. Usually `-key` is a different key from the CA but isn't required to be.

If the addresses haven't been collected by the script above,
then the significant part is that `-subj "CN/="` should be the most public IP
but "subjectAltName" permits a certificate to be used for multiple addresses
and is specified as every address having the prefix `IP:` and separated by `,`
e.g. `-addext subjectAltName=IP:203.0.113.46,IP:192.168.1.10,IP:2001:db8::4d`.


```
openssl req -new -key ~/.maptool-rptools/config/private.key -out ~/.maptool-rptools/config/ca/csr/server-1.csr -subj "/CN=${ips[0]}" -addext "subjectAltName=$(IFS=","; echo "${subjectAltName[*]}")"
```

Now create the certificate by signing the CSR.

Note this is issued for 1 day because that is the minimum
and `-copy_extensions copy` is used to copy subjectAltName into the certificate.

```
openssl x509 -req -in ~/.maptool-rptools/config/ca/csr/server-1.csr -copy_extensions copy -CA ~/.maptool-rptools/config/ca/certs/ca.crt -CAkey ~/.maptool-rptools/config/private.key -CAcreateserial -days 1 -out ~/.maptool-rptools/config/ca/certs/server-1.crt
```

## 3. Starting a SSL enabled MapTool server

**NOTE**: These manual steps are intended to be automated later.

Without native SSL support, MapTool must use an SSL tunnel.

`socat` requires a certificate bundled with the private key.
We can create this with the following command:

```
openssl rsa -in ~/.maptool-rptools/config/private.key | cat - ~/.maptool-rptools/config/ca/certs/server-1.crt >~/.maptool-rptools/config/ca/certs/server-1.pem
```

We can now create the server tunnel.

```
socat OPENSSL-LISTEN:51232,cert="$HOME/.maptool-rptools/config/ca/certs/server-1.pem",verify=0 TCP:127.0.0.1:51234
```

where 51234 is the port the server is listening on and 51232 is a free port.

While this command is running an SSL connection to 51232 will connect
to a MapTool server running on port 51234.

## 4. Adding certificates to the client's trust store

### Installing certificates into MapTool's trust store

First locate the certificate. If it's a manually managed certificate it will be
`~/.maptool-rptools/config/ca/certs/ca.crt`.
If instead it is inside a key store it can be extracted with:

```
openssl pkcs12 -in ~/.maptool-rptools/config/keystore.p12 -nokeys -passin pass:"$(secret-tool lookup service maptool-rptools account '')" | openssl x509 -out ca.crt
```

If the trust store doesn't already exist run:

```
openssl pkcs12 -export -out ~/.maptool-rptools/config/truststore.p12 -passout pass:"$(secret-tool lookup service maptool-rptools account '')" -jdktrust anyExtendedKeyUsage -in ca.crt
```

If it does instead the new certificate can be added with:

```
openssl pkcs12 -export -in ~/.maptool-rptools/config/truststore.p12 -passin pass:"$(secret-tool lookup service maptool-rptools account '')" -out ~/.maptool-rptools/config/truststore.p12 -passout pass:"$(secret-tool lookup service maptool-rptools account '')" -jdktrust anyExtendedKeyUsage -certfile ca.crt
```

### Installing certificates into the OS trust store

This is not recommended because these certificates are available system-wide
instead of being only available to MapTool,
and would allow your GM to compromise your connections to web servers.

```
sudo install -D -m644 ~/.maptool-rptools/config/ca/certs/ca.crt /usr/local/share/ca-certificates/extra/maptool-$(cat ~/.maptool-rptools/client-id)-root-ca.crt
sudo update-ca-certificates
```

## 5. Connecting to a SSL enabled MapTool server directly

With the certificate installed in the system certificate store
the second `TCP-LISTEN` socat tunnel can be omitted
and the new `rptools-maptool+tcps://` scheme used
to connect from the command-line to port 51232.

```
./gradlew run --args=rptools-maptool+tcps://${localips[0]}:51232
```

Alternatively the "Use SSL" checkbox in the "Direct" tab of the connect dialog
can be checked to specify to connect with SSL.

## 6. Connecting to a SSL enabled MapTool server using a tunnel

Without native SSL support, MapTool must use an SSL tunnel.

### Creating a tunnel using the certificate store

The command to create an SSL tunnel that a client can connect to in order to create an ssl connection is:

```
socat TCP-LISTEN:51231 OPENSSL:"${localips[0]}":51232,cafile="$HOME/.maptool-rptools/config/ca/certs/ca.crt",snihost"=${localips[0]}"
```

While this command is running MapTool can connect to port 51231
to make a SSL connection via the first local IP address.
`snihost=` must match one of the addresses in `subjectAltName`.

### Using the system certificate store

If the certificates for the server are installed into the OS certificate store
the command to create the tunnel becomes:

```
socat TCP-LISTEN:51231 OPENSSL:"${localips[0]}":51232,snihost"=${localips[0]}"
```

### Connecting with MapTool through the tunnel

Using the URI support in develop's version of MapTool we can instruct MapTool to connect through the proxy.

```
/opt/maptool/bin/MapTool-Develop rptools-maptool+tcp://${localips[0]}:51231
```
or while running from git
```
./gradlew run --args=rptools-maptool+tcp://${localips[0]}:51231
```
