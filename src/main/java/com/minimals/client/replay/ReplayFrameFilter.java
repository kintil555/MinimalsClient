package com.minimals.client.replay;

/**
 * Drops recorded frames that only make sense on the live connection and break a replay.
 *
 * The recorder stores every clientbound frame, including the ones a remote (multiplayer) server
 * sends during login. The replay world is joined over an in-memory pipe, exactly like an
 * integrated server, where none of these are ever sent:
 * <ul>
 *   <li>login HELLO: encryption request. handleHello authenticates against the Mojang session
 *       server with a digest that never existed (join fails), or installs a cipher on the local
 *       pipe that garbles every frame after it.</li>
 *   <li>login COMPRESSION: skipped by the client for memory connections anyway; dropped for clarity.</li>
 *   <li>login DISCONNECT, configuration/play DISCONNECT and TRANSFER: would close the replay or make
 *       the client connect to another server.</li>
 * </ul>
 * Ids are the registration order of the clientbound protocols in 26.2 (the play protocol registers
 * the bundle delimiter first, so its ids are the list index + 1).
 */
public final class ReplayFrameFilter {

    private static final int LOGIN_DISCONNECT = 0;
    private static final int LOGIN_HELLO = 1;
    private static final int LOGIN_COMPRESSION = 3;

    private static final int CONFIG_DISCONNECT = 2;
    private static final int CONFIG_TRANSFER = 11;

    private static final int PLAY_DISCONNECT = 32;
    private static final int PLAY_TRANSFER = 129;

    private ReplayFrameFilter() {
    }

    /** True when the frame must not be fed to the replay connection. */
    public static boolean drop(ReplayFormat.Frame frame) {
        int id = packetId(frame.data());
        if (id < 0) {
            return false;
        }
        return switch (frame.protocol()) {
            case ReplayFormat.PROTO_LOGIN -> id == LOGIN_DISCONNECT || id == LOGIN_HELLO || id == LOGIN_COMPRESSION;
            case ReplayFormat.PROTO_CONFIG -> id == CONFIG_DISCONNECT || id == CONFIG_TRANSFER;
            case ReplayFormat.PROTO_PLAY -> id == PLAY_DISCONNECT || id == PLAY_TRANSFER;
            default -> false;
        };
    }

    /** Packet ids are a leading VarInt. */
    private static int packetId(byte[] data) {
        int value = 0;
        for (int i = 0; i < 5 && i < data.length; i++) {
            int b = data[i];
            value |= (b & 0x7F) << (7 * i);
            if ((b & 0x80) == 0) {
                return value;
            }
        }
        return -1;
    }
}
