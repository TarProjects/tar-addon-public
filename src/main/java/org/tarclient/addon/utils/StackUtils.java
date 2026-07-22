package org.tarclient.addon.utils;

import java.util.List;
import java.util.Optional;

public class StackUtils {
    public static Optional<StackWalker.StackFrame> getNthCaller(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("Index N must be >= 0");
        }

        return StackWalker.getInstance().walk(stackFrameStream -> stackFrameStream.skip(n).findFirst());
    }

    public static List<StackWalker.StackFrame> getCallersUpToN(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("Limit N must >= 0");
        }

        return StackWalker.getInstance().walk(stream -> stream
            .limit(n + 1)
            .toList()
        );
    }
}
