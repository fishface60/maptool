Encrypted Key Store
===================

Instead of using a key pair stored on disk, a key store may be used.

First, convert the private key into PEM format since `openssl pkcs12`
can't use them in the existing format.

```
openssl rsa -in ~/.maptool-rptools/config/private.key -outform PEM -out ~/.maptool-rptools/config/private.pem
```

Then create a certificate that contains the public key
since the PKCS12 format is intended for web servers' key bundles.

```
mkdir -p ~/.maptool-rptools/config/ca/certs/
openssl req -new -x509 -days 3650 -key ~/.maptool-rptools/config/private.key -out ~/.maptool-rptools/config/ca/certs/ca.crt -subj "/CN=MapTool $(< ~/.maptool-rptools/client-id) CA Root"
```

Now create the PKCS12 bundle by running this command and entering a password.

```
openssl pkcs12 -export -name MapToolKeyPair -inkey ~/.maptool-rptools/config/private.pem -in ~/.maptool-rptools/config/ca/certs/ca.crt -chain -out ~/.maptool-rptools/config/keystore.p12
```

This certificate bundle is placed in the MapTool config somewhere it can locate it
with an expected alias and password.

To enable MapTool to read the password to unlock the key store on Linux
the following command can be run and the password entered.

```
secret-tool store --label="maptool-rptools|" service "maptool-rptools" account ""
```

Should you wish, you can now remove the existing key files.

```
rm ~/.maptool-rptools/config/{public,private}.key
```

To return to using key pair files, remove `~/.maptool-rptools/config/keystore.p12`
and new keys will be generated next time they are loaded.
