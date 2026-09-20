package com.minimals.client.replay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Layout state of the replay editor that must survive the timeline screen being closed and
 * reopened: which panels are collapsed, the Visuals scroll offset, the sizing choice and the list
 * of timeline tracks. Only the GUI structure lives here; no track holds keyframes yet.
 */
public final class ReplayEditorState {

    public static final int MAX_TRACKS = 6;

    public enum TrackType {
        TIMELAPSE("Timelapse"),
        CAMERA("Camera"),
        FOV("FOV");

        private final String label;

        TrackType(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum Sizing {
        KEEP_ASPECT("Keep Aspect Ratio"),
        STRETCH("Stretch to Fill");

        private final String label;

        Sizing(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        public Sizing next() {
            Sizing[] all = values();
            return all[(ordinal() + 1) % all.length];
        }
    }

    private static final List<TrackType> TRACKS = new ArrayList<>();

    private static boolean visualsCollapsed;
    private static boolean timelineCollapsed;
    private static int visualsScroll;
    private static Sizing sizing = Sizing.KEEP_ASPECT;

    static {
        reset();
    }

    private ReplayEditorState() {
    }

    public static List<TrackType> tracks() {
        return Collections.unmodifiableList(TRACKS);
    }

    /** Adds a track row; ignored once {@link #MAX_TRACKS} is reached. */
    public static void addTrack(TrackType type) {
        if (TRACKS.size() < MAX_TRACKS) {
            TRACKS.add(type);
        }
    }

    public static boolean visualsCollapsed() {
        return visualsCollapsed;
    }

    public static void toggleVisualsCollapsed() {
        visualsCollapsed = !visualsCollapsed;
    }

    public static boolean timelineCollapsed() {
        return timelineCollapsed;
    }

    public static void toggleTimelineCollapsed() {
        timelineCollapsed = !timelineCollapsed;
    }

    public static int visualsScroll() {
        return visualsScroll;
    }

    public static void setVisualsScroll(int value) {
        visualsScroll = Math.max(0, value);
    }

    public static Sizing sizing() {
        return sizing;
    }

    public static void cycleSizing() {
        sizing = sizing.next();
    }

    public static void reset() {
        TRACKS.clear();
        TRACKS.add(TrackType.TIMELAPSE);
        TRACKS.add(TrackType.CAMERA);
        TRACKS.add(TrackType.FOV);
        visualsCollapsed = false;
        timelineCollapsed = false;
        visualsScroll = 0;
        sizing = Sizing.KEEP_ASPECT;
    }
}
