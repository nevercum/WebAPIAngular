/*
 * Copyright (c) 2002-2017, the original author or authors.
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package jdk.internal.org.jline.utils;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;

public class PumpReader extends Reader {

    private static final int EOF = -1;
    private static final int DEFAULT_BUFFER_SIZE = 4096;

    // Read and write buffer are backed by the same array
    private final CharBuffer readBuffer;
    private final CharBuffer writeBuffer;

    private final Writer writer;

    private boolean closed;

    public PumpReader() {
        this(DEFAULT_BUFFER_SIZE);
    }

    public PumpReader(int bufferSize) {
        char[] buf = new char[bufferSize];
        this.readBuffer = CharBuffer.wrap(buf);
        this.writeBuffer = CharBuffer.wrap(buf);
        this.writer = new Writer(this);

        // There are no bytes available to read after initialization
        readBuffer.limit(0);
    }

    public java.io.Writer getWriter() {
        return this.writer;
    }

    public java.io.InputStream createInputStream(Charset charset) {
        return new InputStream(this, charset);
    }

    private boolean wait(CharBuffer buffer) throws InterruptedIOException {
        if (closed) {
            return false;
        }

        while (!buffer.hasRemaining()) {
            // Wake up waiting readers/writers
            notifyAll();

            try {
                wait();
            } catch (InterruptedException e) {
                throw new InterruptedIOException();
            }

            if (closed) {
                return false;
            }
        }

        return true;
    }

    /**
     * Blocks until more input is available or the reader is closed.
     *
     * @return true if more input is available, false if the reader is closed
     * @throws InterruptedIOException If {@link #wait()} is interrupted
     */
    private boolean waitForInput() throws InterruptedIOException {
        return wait(readBuffer);
    }

    /**
     * Blocks until there is new space available for buffering or the
     * reader is closed.
     *
     * @throws InterruptedIOException If {@link #wait()} is interrupted
     * @throws ClosedException If the reader was closed
     */
    private void waitForBufferSpace() throws InterruptedIOException, ClosedException {
        if (!wait(writeBuffer)) {
            throw new ClosedException();
        }
    }

    private static boolean rewind(CharBuffer buffer, CharBuffer other) {
        // Extend limit of other buffer if there is additional input/output available
        if (buffer.position() > other.position()) {
            other.limit(buffer.position());
        }

        // If we have reached the end of the buffer, rewind and set the new limit
        if (buffer.position() == buffer.capacity()) {
            buffer.rewind();
            buffer.limit(other.position());
            return true;
        } else {
            return false;
        }
    }

    /**
     * Attempts to find additional input by rewinding the {@link #readBuffer}.
     * Updates the {@link #writeBuffer} to make read bytes available for buffering.
     *
     * @return If more input is available
     */
    private boole