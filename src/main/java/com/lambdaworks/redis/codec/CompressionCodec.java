package com.lambdaworks.redis.codec;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.InflaterInputStream;

import com.lambdaworks.redis.internal.LettuceAssert;

/**
 * A compressing/decompressing {@link RedisCodec} that wraps a typed {@link RedisCodec codec} and compresses values using GZIP
 * or Deflate. See {@link com.lambdaworks.redis.codec.CompressionCodec.CompressionType} for supported compression types.
 * 
 * @author Mark Paluch
 */
public class CompressionCodec {

    /**
     * A {@link RedisCodec} that compresses values from a delegating {@link RedisCodec}.
     * 
     * @param delegate codec used for key-value encoding/decoding, must not be {@literal null}.
     * @param compressionType the compression type, must not be {@literal null}.
     * @param <K> Key type.
     * @param <V> Value type.
     * @return Value-compressing codec.
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static <K, V> RedisCodec<K, V> valueCompressor(RedisCodec<K, V> delegate, CompressionType compressionType) {
        LettuceAssert.notNull(delegate, "RedisCodec must not be null");
        LettuceAssert.notNull(compressionType, "CompressionType must not be null");
        return (RedisCodec) new CompressingValueCodecWrapper((RedisCodec) delegate, compressionType);
    }

    private static class CompressingValueCodecWrapper implements RedisCodec<Object, Object> {

        private RedisCodec<Object, Object> delegate;
        private CompressionType compressionType;

        public CompressingValueCodecWrapper(RedisCodec<Object, Object> delegate, CompressionType compressionType) {
            this.delegate = delegate;
            this.compressionType = compressionType;
        }

        @Override
        public Object decodeKey(ByteBuffer bytes) {
            return delegate.decodeKey(bytes);
        }

        @Override
        public Object decodeValue(ByteBuffer bytes) {
            try {
                return delegate.decodeValue(decompress(bytes));
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public ByteBuffer encodeKey(Object key) {
            return delegate.encodeKey(key);
        }

        @Override
        public ByteBuffer encodeValue(Object value) {
            try {
                return compress(delegate.encodeValue(value));
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        private ByteBuffer compress(ByteBuffer source) throws IOException {
            if (source.remaining() == 0) {
                return source;
            }

            ByteBufferInputStream sourceStream = new ByteBufferInputStream(source);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream(source.remaining() / 2);
            OutputStream compressor = null;
            if (compressionType == CompressionType.GZIP) {
                compressor = new GZIPOutputStream(outputStream);
            }

            if (compressionType == CompressionType.DEFLATE) {
                compressor = new DeflaterOutputStream(outputStream);
            }

            try {
                copy(sourceStream, compressor);
            } finally {
                compressor.close();
            }

            return ByteBuffer.wrap(outputStream.toByteArray());
        }

        private ByteBuffer decompress(ByteBuffer source) throws IOException {
            if (source.remaining() == 0) {
                return source;
            }

            ByteBufferInputStream sourceStream = new ByteBufferInputStream(source);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream(source.remaining() * 2);
            InputStream decompressor = null;
            if (compressionType == CompressionType.GZIP) {
                decompressor = new GZIPInputStream(sourceStream);
            }

            if (compressionType == CompressionType.DEFLATE) {
                decompressor = new InflaterInputStream(sourceStream);
            }

            try {
                copy(decompressor, outputStream);
            } finally {
                decompressor.close();
            }

            return ByteBuffer.wrap(outputStream.toByteArray());
        }

    }

    /**
     * Copies all bytes from the input stream to the output stream. Does not close or flush either stream.
     *
     * @param from the input stream to read from
     * @param to the output stream to write to
     * @return the number of bytes copied
     * @throws IOException if an I/O error occurs
     */
    private static long copy(InputStream from, OutputStream to) throws IOException {
        LettuceAssert.notNull(from, "From must not be null");
        LettuceAssert.notNull(to, "From must not be null");
        byte[] buf = new byte[4096];
        long total = 0;
        while (true) {
            int r = from.read(buf);
            if (r == -1) {
                break;
            }
            to.write(buf, 0, r);
            total += r;
        }
        return total;
    }

    public enum CompressionType {
        GZIP, DEFLATE;
    }

}
