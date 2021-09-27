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
package net.rptools.maptool.server;

import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;

import net.rptools.lib.MD5Key;
import net.rptools.maptool.client.walker.WalkerMetric;
import net.rptools.maptool.model.drawing.*;
import net.rptools.maptool.server.proto.ServerPolicyDto;
import net.rptools.maptool.server.proto.WalkerMetricDto;
import net.rptools.maptool.server.proto.drawing.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Mapper {
  /** Instance used for log messages. */
  private static final Logger log = LogManager.getLogger(Mapper.class);

  public static ServerPolicy map(ServerPolicyDto source) {
    var destination = new ServerPolicy();
    destination.setUseStrictTokenManagement(source.getUseStrictTokenManagement());
    destination.setIsMovementLocked(source.getIsMovementLocked());
    destination.setIsTokenEditorLocked(source.getIsTokenEditorLocked());
    destination.setPlayersCanRevealVision(source.getPlayersCanRevealVision());
    destination.setGmRevealsVisionForUnownedTokens(source.getGmRevealsVisionForUnownedTokens());
    destination.setUseIndividualViews(source.getUseIndividualViews());
    destination.setRestrictedImpersonation(source.getRestrictedImpersonation());
    destination.setPlayersReceiveCampaignMacros(source.getPlayersReceiveCampaignMacros());
    destination.setUseToolTipsForDefaultRollFormat(source.getUseToolTipsForDefaultRollFormat());
    destination.setUseIndividualFOW(source.getUseIndividualFOW());
    destination.setAutoRevealOnMovement(source.getIsAutoRevealOnMovement());
    destination.setIncludeOwnedNPCs(source.getIncludeOwnedNPCs());
    destination.setMovementMetric(WalkerMetric.valueOf(source.getMovementMetric().name()));
    destination.setUsingAstarPathfinding(source.getUsingAstarPathfinding());
    destination.setVblBlocksMove(source.getVblBlocksMove());
    return destination;
  }

  public static ServerPolicyDto map(ServerPolicy source) {
    var destination = ServerPolicyDto.newBuilder();
    destination.setUseStrictTokenManagement(source.useStrictTokenManagement());
    destination.setIsMovementLocked(source.isMovementLocked());
    destination.setIsTokenEditorLocked(source.isTokenEditorLocked());
    destination.setPlayersCanRevealVision(source.getPlayersCanRevealVision());
    destination.setGmRevealsVisionForUnownedTokens(source.getGmRevealsVisionForUnownedTokens());
    destination.setUseIndividualViews(source.isUseIndividualViews());
    destination.setRestrictedImpersonation(source.isRestrictedImpersonation());
    destination.setPlayersReceiveCampaignMacros(source.playersReceiveCampaignMacros());
    destination.setUseToolTipsForDefaultRollFormat(source.getUseToolTipsForDefaultRollFormat());
    destination.setUseIndividualFOW(source.isUseIndividualFOW());
    destination.setIsAutoRevealOnMovement(source.isAutoRevealOnMovement());
    destination.setIncludeOwnedNPCs(source.isIncludeOwnedNPCs());
    destination.setMovementMetric(WalkerMetricDto.valueOf(source.getMovementMetric().name()));
    destination.setUsingAstarPathfinding(source.isUsingAstarPathfinding());
    destination.setVblBlocksMove(source.getVblBlocksMove());
    return destination.build();
  }

  public static Area map(AreaDto areaDto) {
    var segmentIterator = areaDto.getSegmentsList().iterator();

    var it =
        new PathIterator() {
          private SegmentDto currentSegment = segmentIterator.next();

          @Override
          public int getWindingRule() {
            return areaDto.getWindingValue();
          }

          @Override
          public boolean isDone() {
            return !segmentIterator.hasNext();
          }

          @Override
          public void next() {
            currentSegment = segmentIterator.next();
          }

          @Override
          public int currentSegment(float[] coords) {
            switch (currentSegment.getSegmentTypeCase()) {
              case MOVE_TO -> {
                var segment = currentSegment.getMoveTo();
                var point0 = segment.getPoint0();
                coords[0] = (float) point0.getX();
                coords[1] = (float) point0.getY();
                return PathIterator.SEG_MOVETO;
              }
              case LINE_TO -> {
                var segment = currentSegment.getLineTo();
                var point0 = segment.getPoint0();
                coords[0] = (float) point0.getX();
                coords[1] = (float) point0.getY();
                return PathIterator.SEG_LINETO;
              }
              case QUAD_TO -> {
                var segment = currentSegment.getQuadTo();
                var point0 = segment.getPoint0();
                coords[0] = (float) point0.getX();
                coords[1] = (float) point0.getY();
                var point1 = segment.getPoint1();
                coords[2] = (float) point1.getX();
                coords[3] = (float) point1.getY();
                return PathIterator.SEG_QUADTO;
              }
              case CUBIC_TO -> {
                var segment = currentSegment.getCubicTo();
                var point0 = segment.getPoint0();
                coords[0] = (float) point0.getX();
                coords[1] = (float) point0.getY();
                var point1 = segment.getPoint1();
                coords[2] = (float) point1.getX();
                coords[3] = (float) point1.getY();
                var point2 = segment.getPoint2();
                coords[4] = (float) point2.getX();
                coords[5] = (float) point2.getY();
                return PathIterator.SEG_CUBICTO;
              }
            }
            return SEG_CLOSE;
          }

          @Override
          public int currentSegment(double[] coords) {
            switch (currentSegment.getSegmentTypeCase()) {
              case MOVE_TO -> {
                var segment = currentSegment.getMoveTo();
                var point0 = segment.getPoint0();
                coords[0] = point0.getX();
                coords[1] = point0.getY();
                return PathIterator.SEG_MOVETO;
              }
              case LINE_TO -> {
                var segment = currentSegment.getLineTo();
                var point0 = segment.getPoint0();
                coords[0] = point0.getX();
                coords[1] = point0.getY();
                return PathIterator.SEG_LINETO;
              }
              case QUAD_TO -> {
                var segment = currentSegment.getQuadTo();
                var point0 = segment.getPoint0();
                coords[0] = point0.getX();
                coords[1] = point0.getY();
                var point1 = segment.getPoint1();
                coords[2] = point1.getX();
                coords[3] = point1.getY();
                return PathIterator.SEG_QUADTO;
              }
              case CUBIC_TO -> {
                var segment = currentSegment.getCubicTo();
                var point0 = segment.getPoint0();
                coords[0] = point0.getX();
                coords[1] = point0.getY();
                var point1 = segment.getPoint1();
                coords[2] = point1.getX();
                coords[3] = point1.getY();
                var point2 = segment.getPoint2();
                coords[4] = point2.getX();
                coords[5] = point2.getY();
                return PathIterator.SEG_CUBICTO;
              }
            }
            return SEG_CLOSE;
          }
        };
    var path = new Path2D.Float();
    path.append(it, false);
    return new Area(path);
  }

  public static AreaDto map(Area area) {
    var builder = AreaDto.newBuilder();

    var it = area.getPathIterator(null);
    float[] floats = new float[6];
    builder.setWinding(AreaDto.WindingRule.forNumber(it.getWindingRule()));

    for (; !it.isDone(); it.next()) {
      var segmentBuilder = SegmentDto.newBuilder();
      switch (it.currentSegment(floats)) {
        case PathIterator.SEG_MOVETO -> {
          var point0Builder = DoublePointDto.newBuilder().setX(floats[0]).setY(floats[1]);
          var moveTo = MoveToSegment.newBuilder().setPoint0(point0Builder);
          segmentBuilder.setMoveTo(moveTo);
        }
        case PathIterator.SEG_LINETO -> {
          var point0Builder = DoublePointDto.newBuilder().setX(floats[0]).setY(floats[1]);
          var lineTo = LineToSegment.newBuilder().setPoint0(point0Builder);
          segmentBuilder.setLineTo(lineTo);
        }
        case PathIterator.SEG_QUADTO -> {
          var point0Builder = DoublePointDto.newBuilder().setX(floats[0]).setY(floats[1]);
          var point1Builder = DoublePointDto.newBuilder().setX(floats[2]).setY(floats[3]);
          var quadTo = QuadToSegment.newBuilder().setPoint0(point0Builder).setPoint1(point1Builder);
          segmentBuilder.setQuadTo(quadTo);
        }
        case PathIterator.SEG_CUBICTO -> {
          var point0Builder = DoublePointDto.newBuilder().setX(floats[0]).setY(floats[1]);
          var point1Builder = DoublePointDto.newBuilder().setX(floats[2]).setY(floats[3]);
          var point2Builder = DoublePointDto.newBuilder().setX(floats[4]).setY(floats[5]);
          var cubicTo =
              CubicToSegment.newBuilder()
                  .setPoint0(point0Builder)
                  .setPoint1(point1Builder)
                  .setPoint2(point2Builder);
          segmentBuilder.setCubicTo(cubicTo);
        }
        case PathIterator.SEG_CLOSE -> segmentBuilder.setClose(CloseSegment.newBuilder());
      }
      builder.addSegments(segmentBuilder);
    }

    return builder.build();
  }

  public static DrawablePaint map(DrawablePaintDto dto) {
    switch(dto.getPaintTypeCase()) {
      case COLOR_PAINT -> {
        var paint = new DrawableColorPaint(dto.getColorPaint().getColor());
        return paint;
      }
      case TEXTURE_PAINT -> {
        var texturePaintDto = dto.getTexturePaint();
        return new DrawableTexturePaint(new MD5Key(texturePaintDto.getAssetId()), texturePaintDto.getScale());
      }
      default -> {
        log.warn("unknown DrawablePaintDto type: " + dto.getPaintTypeCase());
        return null;
      }
    }
  }

  public static DrawablePaintDto map(DrawablePaint paint) {
    var dto = DrawablePaintDto.newBuilder();
    if(paint instanceof DrawableColorPaint colorPaint) {
      return dto.setColorPaint(DrawableColorPaintDto.newBuilder().setColor(colorPaint.getColor())).build();
    } else if (paint instanceof  DrawableTexturePaint texturePaint){
      var textureDto = DrawableTexturePaintDto.newBuilder()
          .setAssetId(texturePaint.getAssetId().toString())
          .setScale(texturePaint.getScale());
      return dto.setTexturePaint(textureDto).build();
    }
    log.warn("unexpected type " + paint.getClass().getName());
    return null;
  }

  public static Pen map(PenDto penDto) {
    var pen = new Pen();
    pen.setEraser(penDto.getEraser());
    pen.setForegroundMode(penDto.getForegroundModeValue());
    pen.setBackgroundMode(penDto.getBackgroundModeValue());
    pen.setThickness(penDto.getThickness());
    pen.setOpacity(penDto.getOpacity());
    pen.setSquareCap(penDto.getSquareCap());
    pen.setPaint(map(penDto.getForegroundColor()));
    pen.setBackgroundPaint(map(penDto.getBackgroundColor()));
    return pen;
  }

  public static PenDto map(Pen pen) {
    return PenDto.newBuilder()
        .setEraser(pen.isEraser())
        .setForegroundMode(PenDto.mode.forNumber(pen.getForegroundMode()))
        .setBackgroundMode(PenDto.mode.forNumber(pen.getBackgroundMode()))
        .setThickness(pen.getThickness())
        .setOpacity(pen.getOpacity())
        .setSquareCap(pen.getSquareCap())
        .setForegroundColor(map(pen.getPaint()))
        .setBackgroundColor(map(pen.getBackgroundPaint()))
        .build();
  }

  public static Drawable map(DrawableDto dto) {
    switch (dto.getDrawableTypeCase()) {
      case SHAPE_DRAWABLE -> {
        return null;
      }
      case RECTANGLE_DRAWABLE -> {
        return null;
      }
      case OVAL_DRAWABLE -> {
        return null;
      }
      case CROSS_DRAWABLE -> {
        return null;
      }
      case DRAWN_LABEL -> {
        return null;
      }
      case LINE_SEGMENT -> {
        return null;
      }
      case DRAWABLES_GROUP -> {
        return null;
      }
      case RADIUS_CELL_TEMPLATE -> {
        return null;
      }
      case LINE_CELL_TEMPLATE -> {
        return null;
      }
      case RADIUS_TEMPLATE -> {
        return null;
      }
      case BURST_TEMPLATE -> {
        return null;
      }
      case CONE_TEMPLATE -> {
        return null;
      }
      case BLAST_TEMPLATE -> {
        return null;
      }
      case LINE_TEMPLATE -> {
        return null;
      }
      case WALL_TEMPLATE -> {
        return null;
      }
      default -> {
        log.warn("unknown DrawableDto type: " + dto.getDrawableTypeCase());
        return null;
      }
    }
  }

  public static DrawableDto map(Drawable drawable) {
    return null;
  }
}
