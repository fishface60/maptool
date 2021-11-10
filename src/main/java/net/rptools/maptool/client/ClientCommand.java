/*
 * This software Copyright by the RPTools.net development team, and
 * licensed under the Affero GPL Version 3 or, at your option, any later
 * version.
 *
 * MapTool Source Code is distributed in the hope that it will be
 * useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * You should have received a copy of the GNU Affero General Public
 * License * along with this source Code.  If not, please visit
 * <http://www.gnu.org/licenses/> and specifically the Affero license
 * text at <http://www.gnu.org/licenses/agpl.html>.
 */
package net.rptools.maptool.client;

public class ClientCommand {

  public enum COMMAND {
    // @formatter:off
    startAssetTransfer,
    updateAssetTransfer,
    setCampaign,
    removeZone,
    removeAsset,
    updateTokenProperty,
    removeToken,
    removeTokens,
    updateDrawing,
    setZoneGridSize,
    setZoneVisibility,
    undoDraw,
    showPointer,
    startTokenMove,
    stopTokenMove,
    toggleTokenMoveWaypoint,
    updateTokenMove,
    setZoneHasFoW,
    setFoW,
    removeLabel,
    setServerPolicy,
    removeTopology,
    renameZone,
    updateCampaign,
    updateInitiative,
    updateTokenInitiative,
    setUseVision,
    updateCampaignMacros,
    updateGmMacros,
    setTokenLocation, // NOTE: This is to support third party token placement and shouldn't be
    // depended on for general purpose token movement
    setLiveTypingLabel, // Experimental chat notification
    setBoard,
    updateExposedAreaMeta,
    setCampaignName,
    restoreZoneView, // Jamz: New command to restore player's view and let GM temporarily center and
    addAddOnLibrary,
    removeAllAddOnLibraries,
    removeAddOnLibrary,
    updateDataStore,
    updateData,
    updateDataNamespace,
    removeDataStore,
    removeDataNamespace,
    removeData
    // scale a player's view
    // @formatter:on
  };
}
