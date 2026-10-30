package io.github.tjhgibbs.capella.core.kit;

/** Which faces of a {@link Geometry.Shell} are drawn. */
public record ShellFaces(boolean top, boolean bottom, boolean north, boolean south, boolean east, boolean west) {
    public static final ShellFaces ALL = new ShellFaces(true, true, true, true, true, true);

    public ShellFaces withTop(boolean v) {
        return new ShellFaces(v, bottom, north, south, east, west);
    }

    public ShellFaces withBottom(boolean v) {
        return new ShellFaces(top, v, north, south, east, west);
    }

    public ShellFaces withNorth(boolean v) {
        return new ShellFaces(top, bottom, v, south, east, west);
    }

    public ShellFaces withSouth(boolean v) {
        return new ShellFaces(top, bottom, north, v, east, west);
    }

    public ShellFaces withEast(boolean v) {
        return new ShellFaces(top, bottom, north, south, v, west);
    }

    public ShellFaces withWest(boolean v) {
        return new ShellFaces(top, bottom, north, south, east, v);
    }
}
