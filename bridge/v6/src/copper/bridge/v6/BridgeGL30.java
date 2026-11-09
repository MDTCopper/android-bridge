package copper.bridge.v6;

import copper.bridge.annotation.BridgeInterface;

import arc.graphics.*;
import java.nio.*;
import org.lwjgl.*;
import org.lwjgl.opengles.*;
import org.lwjgl.system.*;

/**
 * JVM-side implementation of arc's OpenGL ES 3.0 interface, layered on top of {@link BridgeGL20}: only the ES
 * 3.0 additions live here, and every call travels through LWJGL's OpenGL ES bindings for the reason given
 * there.
 */
public class BridgeGL30 extends BridgeGL20 implements GL30 {

    @BridgeInterface
    public void glReadBuffer(int mode) {
        GLES30.glReadBuffer(mode);
    }

    @BridgeInterface
    public void glDrawRangeElements(int mode, int start, int end, int count, int type, Buffer indices) {
        GLES30.glDrawRangeElements(mode, start, end, count, type, bufferAddress(indices));
    }

    @BridgeInterface
    public void glDrawRangeElements(int mode, int start, int end, int count, int type, int offset) {
        GLES30.glDrawRangeElements(mode, start, end, count, type, (long)offset);
    }

    @BridgeInterface
    public void glTexImage3D(int target, int level, int internalformat, int width, int height, int depth, int border, int format, int type, Buffer pixels) {
        GLES30.glTexImage3D(target, level, internalformat, width, height, depth, border, format, type, bufferAddress(pixels));
    }

    @BridgeInterface
    public void glTexImage3D(int target, int level, int internalformat, int width, int height, int depth, int border, int format, int type, int offset) {
        GLES30.glTexImage3D(target, level, internalformat, width, height, depth, border, format, type, (long)offset);
    }

    @BridgeInterface
    public void glTexSubImage3D(int target, int level, int xoffset, int yoffset, int zoffset, int width, int height, int depth, int format, int type, Buffer pixels) {
        GLES30.glTexSubImage3D(target, level, xoffset, yoffset, zoffset, width, height, depth, format, type, bufferAddress(pixels));
    }

    @BridgeInterface
    public void glTexSubImage3D(int target, int level, int xoffset, int yoffset, int zoffset, int width, int height, int depth, int format, int type, int offset) {
        GLES30.glTexSubImage3D(target, level, xoffset, yoffset, zoffset, width, height, depth, format, type, (long)offset);
    }

    @BridgeInterface
    public void glCopyTexSubImage3D(int target, int level, int xoffset, int yoffset, int zoffset, int x, int y, int width, int height) {
        GLES30.glCopyTexSubImage3D(target, level, xoffset, yoffset, zoffset, x, y, width, height);
    }

    @BridgeInterface
    public void glGenQueries(int n, IntBuffer ids) {
        GLES30.nglGenQueries(n, bufferAddress(ids));
    }

    /** Array overload of the older GL30 interface; gone again on the newer end, so no {@code @BridgeInterface}. */
    @BridgeInterface
    public void glGenQueries(int n, int[] ids, int offset) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer buffer = stack.mallocInt(n);
            buffer.position(0);
            GLES30.nglGenQueries(n, bufferAddress(buffer));
            buffer.get(ids, offset, n);
        }
    }

    @BridgeInterface
    public void glDeleteQueries(int n, IntBuffer ids) {
        GLES30.nglDeleteQueries(n, bufferAddress(ids));
    }

    /** Array overload of the older GL30 interface; gone again on the newer end, so no {@code @BridgeInterface}. */
    @BridgeInterface
    public void glDeleteQueries(int n, int[] ids, int offset) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer buffer = stack.mallocInt(n);
            buffer.put(ids, offset, n);
            buffer.position(0);
            GLES30.nglDeleteQueries(n, bufferAddress(buffer));
        }
    }

    @BridgeInterface
    public boolean glIsQuery(int id) {
        return GLES30.glIsQuery(id);
    }

    @BridgeInterface
    public void glBeginQuery(int target, int id) {
        GLES30.glBeginQuery(target, id);
    }

    @BridgeInterface
    public void glEndQuery(int target) {
        GLES30.glEndQuery(target);
    }

    @BridgeInterface
    public void glGetQueryiv(int target, int pname, IntBuffer params) {
        GLES30.glGetQueryiv(target, pname, params);
    }

    @BridgeInterface
    public void glGetQueryObjectuiv(int id, int pname, IntBuffer params) {
        GLES30.glGetQueryObjectuiv(id, pname, params);
    }

    @BridgeInterface
    public boolean glUnmapBuffer(int target) {
        return GLES30.glUnmapBuffer(target);
    }

    @BridgeInterface
    public Buffer glGetBufferPointerv(int target, int pname) {
        long pointer = GLES30.glGetBufferPointer(target, pname);
        if (pointer == 0L) {
            return null;
        }
        try(MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer mapped = stack.mallocLong(1);
            glGetBufferParameteri64v(target, GL_BUFFER_MAP_LENGTH, mapped);
            return MemoryUtil.memByteBuffer(pointer, (int)Math.min(mapped.get(0), Integer.MAX_VALUE));
        }
    }

    @BridgeInterface
    public void glDrawBuffers(int n, IntBuffer bufs) {
        GLES30.nglDrawBuffers(n, bufferAddress(bufs));
    }

    @BridgeInterface
    public void glUniformMatrix2x3fv(int location, int count, boolean transpose, FloatBuffer value) {
        GLES30.nglUniformMatrix2x3fv(location, count, transpose, bufferAddress(value));
    }

    @BridgeInterface
    public void glUniformMatrix3x2fv(int location, int count, boolean transpose, FloatBuffer value) {
        GLES30.nglUniformMatrix3x2fv(location, count, transpose, bufferAddress(value));
    }

    @BridgeInterface
    public void glUniformMatrix2x4fv(int location, int count, boolean transpose, FloatBuffer value) {
        GLES30.nglUniformMatrix2x4fv(location, count, transpose, bufferAddress(value));
    }

    @BridgeInterface
    public void glUniformMatrix4x2fv(int location, int count, boolean transpose, FloatBuffer value) {
        GLES30.nglUniformMatrix4x2fv(location, count, transpose, bufferAddress(value));
    }

    @BridgeInterface
    public void glUniformMatrix3x4fv(int location, int count, boolean transpose, FloatBuffer value) {
        GLES30.nglUniformMatrix3x4fv(location, count, transpose, bufferAddress(value));
    }

    @BridgeInterface
    public void glUniformMatrix4x3fv(int location, int count, boolean transpose, FloatBuffer value) {
        GLES30.nglUniformMatrix4x3fv(location, count, transpose, bufferAddress(value));
    }

    @BridgeInterface
    public void glBlitFramebuffer(int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
        GLES30.glBlitFramebuffer(srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
    }

    @BridgeInterface
    public void glRenderbufferStorageMultisample(int target, int samples, int internalformat, int width, int height) {
        GLES30.glRenderbufferStorageMultisample(target, samples, internalformat, width, height);
    }

    @BridgeInterface
    public void glFramebufferTextureLayer(int target, int attachment, int texture, int level, int layer) {
        GLES30.glFramebufferTextureLayer(target, attachment, texture, level, layer);
    }

    @BridgeInterface
    public void glFlushMappedBufferRange(int target, int offset, int length) {
        GLES30.glFlushMappedBufferRange(target, offset, length);
    }

    @BridgeInterface
    public void glBindVertexArray(int array) {
        GLES30.glBindVertexArray(array);
    }

    @BridgeInterface
    public void glDeleteVertexArrays(int n, IntBuffer arrays) {
        GLES30.nglDeleteVertexArrays(n, bufferAddress(arrays));
    }

    /** Array overload of the older GL30 interface; gone again on the newer end, so no {@code @BridgeInterface}. */
    @BridgeInterface
    public void glDeleteVertexArrays(int n, int[] arrays, int offset) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer buffer = stack.mallocInt(n);
            buffer.put(arrays, offset, n);
            buffer.position(0);
            GLES30.nglDeleteVertexArrays(n, bufferAddress(buffer));
        }
    }

    @BridgeInterface
    public void glGenVertexArrays(int n, IntBuffer arrays) {
        GLES30.nglGenVertexArrays(n, bufferAddress(arrays));
    }

    /** Array overload of the older GL30 interface; gone again on the newer end, so no {@code @BridgeInterface}. */
    @BridgeInterface
    public void glGenVertexArrays(int n, int[] arrays, int offset) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer buffer = stack.mallocInt(n);
            buffer.position(0);
            GLES30.nglGenVertexArrays(n, bufferAddress(buffer));
            buffer.get(arrays, offset, n);
        }
    }

    @BridgeInterface
    public boolean glIsVertexArray(int array) {
        return GLES30.glIsVertexArray(array);
    }

    @BridgeInterface
    public void glBeginTransformFeedback(int primitiveMode) {
        GLES30.glBeginTransformFeedback(primitiveMode);
    }

    @BridgeInterface
    public void glEndTransformFeedback() {
        GLES30.glEndTransformFeedback();
    }

    @BridgeInterface
    public void glBindBufferRange(int target, int index, int buffer, int offset, int size) {
        GLES30.glBindBufferRange(target, index, buffer, offset, size);
    }

    @BridgeInterface
    public void glBindBufferBase(int target, int index, int buffer) {
        GLES30.glBindBufferBase(target, index, buffer);
    }

    @BridgeInterface
    public void glTransformFeedbackVaryings(int program, String[] varyings, int bufferMode) {
        GLES30.glTransformFeedbackVaryings(program, varyings, bufferMode);
    }

    @BridgeInterface
    public void glVertexAttribIPointer(int index, int size, int type, int stride, int offset) {
        GLES30.glVertexAttribIPointer(index, size, type, stride, (long)offset);
    }

    @BridgeInterface
    public void glGetVertexAttribIiv(int index, int pname, IntBuffer params) {
        GLES30.glGetVertexAttribIiv(index, pname, params);
    }

    @BridgeInterface
    public void glGetVertexAttribIuiv(int index, int pname, IntBuffer params) {
        GLES30.glGetVertexAttribIuiv(index, pname, params);
    }

    @BridgeInterface
    public void glVertexAttribI4i(int index, int x, int y, int z, int w) {
        GLES30.glVertexAttribI4i(index, x, y, z, w);
    }

    @BridgeInterface
    public void glVertexAttribI4ui(int index, int x, int y, int z, int w) {
        GLES30.glVertexAttribI4ui(index, x, y, z, w);
    }

    @BridgeInterface
    public void glGetUniformuiv(int program, int location, IntBuffer params) {
        GLES30.glGetUniformuiv(program, location, params);
    }

    @BridgeInterface
    public int glGetFragDataLocation(int program, String name) {
        return GLES30.glGetFragDataLocation(program, name);
    }

    @BridgeInterface
    public void glUniform1uiv(int location, int count, IntBuffer value) {
        GLES30.nglUniform1uiv(location, count, bufferAddress(value));
    }

    @BridgeInterface
    public void glUniform3uiv(int location, int count, IntBuffer value) {
        GLES30.nglUniform3uiv(location, count, bufferAddress(value));
    }

    @BridgeInterface
    public void glUniform4uiv(int location, int count, IntBuffer value) {
        GLES30.nglUniform4uiv(location, count, bufferAddress(value));
    }

    @BridgeInterface
    public void glClearBufferiv(int buffer, int drawbuffer, IntBuffer value) {
        GLES30.glClearBufferiv(buffer, drawbuffer, value);
    }

    @BridgeInterface
    public void glClearBufferuiv(int buffer, int drawbuffer, IntBuffer value) {
        GLES30.glClearBufferuiv(buffer, drawbuffer, value);
    }

    @BridgeInterface
    public void glClearBufferfv(int buffer, int drawbuffer, FloatBuffer value) {
        GLES30.glClearBufferfv(buffer, drawbuffer, value);
    }

    @BridgeInterface
    public void glClearBufferfi(int buffer, int drawbuffer, float depth, int stencil) {
        GLES30.glClearBufferfi(buffer, drawbuffer, depth, stencil);
    }

    @BridgeInterface
    public String glGetStringi(int name, int index) {
        return GLES30.glGetStringi(name, index);
    }

    @BridgeInterface
    public void glCopyBufferSubData(int readTarget, int writeTarget, int readOffset, int writeOffset, int size) {
        GLES30.glCopyBufferSubData(readTarget, writeTarget, readOffset, writeOffset, size);
    }

    @BridgeInterface
    public void glGetUniformIndices(int program, String[] uniformNames, IntBuffer uniformIndices) {
        try(MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer names = stack.mallocPointer(uniformNames.length);
            for (int i = 0; i < uniformNames.length; i++) {
                names.put(i, stack.UTF8(uniformNames[i]));
            }
            GLES30.glGetUniformIndices(program, names, uniformIndices);
        }
    }

    @BridgeInterface
    public void glGetActiveUniformsiv(int program, int uniformCount, IntBuffer uniformIndices, int pname, IntBuffer params) {
        GLES30.nglGetActiveUniformsiv(program, uniformCount, bufferAddress(uniformIndices), pname, bufferAddress(params));
    }

    @BridgeInterface
    public int glGetUniformBlockIndex(int program, String uniformBlockName) {
        return GLES30.glGetUniformBlockIndex(program, uniformBlockName);
    }

    @BridgeInterface
    public void glGetActiveUniformBlockiv(int program, int uniformBlockIndex, int pname, IntBuffer params) {
        GLES30.glGetActiveUniformBlockiv(program, uniformBlockIndex, pname, params);
    }

    /** String overload of the older GL30 interface; gone again on the newer end, so it exists at one end only. */
    @BridgeInterface
    public String glGetActiveUniformBlockName(int program, int uniformBlockIndex) {
        return GLES30.glGetActiveUniformBlockName(program, uniformBlockIndex);
    }

    @BridgeInterface
    public void glGetActiveUniformBlockName(int program, int uniformBlockIndex, Buffer length, Buffer uniformBlockName) {        if (!(uniformBlockName instanceof ByteBuffer)) {
            throw new IllegalArgumentException("Unsupported buffer type for a uniform block name: " + uniformBlockName.getClass().getName());
        }
        if (length != null && !(length instanceof IntBuffer)) {
            throw new IllegalArgumentException("Unsupported buffer type for a uniform block name length: " + length.getClass().getName());
        }
        GLES30.glGetActiveUniformBlockName(program, uniformBlockIndex, (IntBuffer)length, (ByteBuffer)uniformBlockName);
    }

    @BridgeInterface
    public void glUniformBlockBinding(int program, int uniformBlockIndex, int uniformBlockBinding) {
        GLES30.glUniformBlockBinding(program, uniformBlockIndex, uniformBlockBinding);
    }

    @BridgeInterface
    public void glDrawArraysInstanced(int mode, int first, int count, int instanceCount) {
        GLES30.glDrawArraysInstanced(mode, first, count, instanceCount);
    }

    @BridgeInterface
    public void glDrawElementsInstanced(int mode, int count, int type, int indicesOffset, int instanceCount) {
        GLES30.glDrawElementsInstanced(mode, count, type, (long)indicesOffset, instanceCount);
    }

    @BridgeInterface
    public void glGetInteger64v(int pname, LongBuffer params) {
        GLES30.glGetInteger64v(pname, params);
    }

    @BridgeInterface
    public void glGetBufferParameteri64v(int target, int pname, LongBuffer params) {
        GLES30.glGetBufferParameteri64v(target, pname, params);
    }

    @BridgeInterface
    public void glGenSamplers(int count, IntBuffer samplers) {
        GLES30.nglGenSamplers(count, bufferAddress(samplers));
    }

    /** Array overload of the older GL30 interface; gone again on the newer end, so no {@code @BridgeInterface}. */
    @BridgeInterface
    public void glGenSamplers(int count, int[] samplers, int offset) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer buffer = stack.mallocInt(count);
            buffer.position(0);
            GLES30.nglGenSamplers(count, bufferAddress(buffer));
            buffer.get(samplers, offset, count);
        }
    }

    @BridgeInterface
    public void glDeleteSamplers(int count, IntBuffer samplers) {
        GLES30.nglDeleteSamplers(count, bufferAddress(samplers));
    }

    /** Array overload of the older GL30 interface; gone again on the newer end, so no {@code @BridgeInterface}. */
    @BridgeInterface
    public void glDeleteSamplers(int count, int[] samplers, int offset) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer buffer = stack.mallocInt(count);
            buffer.put(samplers, offset, count);
            buffer.position(0);
            GLES30.nglDeleteSamplers(count, bufferAddress(buffer));
        }
    }

    @BridgeInterface
    public boolean glIsSampler(int sampler) {
        return GLES30.glIsSampler(sampler);
    }

    @BridgeInterface
    public void glBindSampler(int unit, int sampler) {
        GLES30.glBindSampler(unit, sampler);
    }

    @BridgeInterface
    public void glSamplerParameteri(int sampler, int pname, int param) {
        GLES30.glSamplerParameteri(sampler, pname, param);
    }

    @BridgeInterface
    public void glSamplerParameteriv(int sampler, int pname, IntBuffer param) {
        GLES30.glSamplerParameteriv(sampler, pname, param);
    }

    @BridgeInterface
    public void glSamplerParameterf(int sampler, int pname, float param) {
        GLES30.glSamplerParameterf(sampler, pname, param);
    }

    @BridgeInterface
    public void glSamplerParameterfv(int sampler, int pname, FloatBuffer param) {
        GLES30.glSamplerParameterfv(sampler, pname, param);
    }

    @BridgeInterface
    public void glGetSamplerParameteriv(int sampler, int pname, IntBuffer params) {
        GLES30.glGetSamplerParameteriv(sampler, pname, params);
    }

    @BridgeInterface
    public void glGetSamplerParameterfv(int sampler, int pname, FloatBuffer params) {
        GLES30.glGetSamplerParameterfv(sampler, pname, params);
    }

    @BridgeInterface
    public void glVertexAttribDivisor(int index, int divisor) {
        GLES30.glVertexAttribDivisor(index, divisor);
    }

    @BridgeInterface
    public void glBindTransformFeedback(int target, int id) {
        GLES30.glBindTransformFeedback(target, id);
    }

    @BridgeInterface
    public void glDeleteTransformFeedbacks(int n, IntBuffer ids) {
        GLES30.nglDeleteTransformFeedbacks(n, bufferAddress(ids));
    }

    /** Array overload of the older GL30 interface; gone again on the newer end, so no {@code @BridgeInterface}. */
    @BridgeInterface
    public void glDeleteTransformFeedbacks(int n, int[] ids, int offset) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer buffer = stack.mallocInt(n);
            buffer.put(ids, offset, n);
            buffer.position(0);
            GLES30.nglDeleteTransformFeedbacks(n, bufferAddress(buffer));
        }
    }

    @BridgeInterface
    public void glGenTransformFeedbacks(int n, IntBuffer ids) {
        GLES30.nglGenTransformFeedbacks(n, bufferAddress(ids));
    }

    /** Array overload of the older GL30 interface; gone again on the newer end, so no {@code @BridgeInterface}. */
    @BridgeInterface
    public void glGenTransformFeedbacks(int n, int[] ids, int offset) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer buffer = stack.mallocInt(n);
            buffer.position(0);
            GLES30.nglGenTransformFeedbacks(n, bufferAddress(buffer));
            buffer.get(ids, offset, n);
        }
    }

    @BridgeInterface
    public boolean glIsTransformFeedback(int id) {
        return GLES30.glIsTransformFeedback(id);
    }

    @BridgeInterface
    public void glPauseTransformFeedback() {
        GLES30.glPauseTransformFeedback();
    }

    @BridgeInterface
    public void glResumeTransformFeedback() {
        GLES30.glResumeTransformFeedback();
    }

    @BridgeInterface
    public void glProgramParameteri(int program, int pname, int value) {
        GLES30.glProgramParameteri(program, pname, value);
    }

    @BridgeInterface
    public void glInvalidateFramebuffer(int target, int numAttachments, IntBuffer attachments) {
        GLES30.nglInvalidateFramebuffer(target, numAttachments, bufferAddress(attachments));
    }

    @BridgeInterface
    public void glInvalidateSubFramebuffer(int target, int numAttachments, IntBuffer attachments, int x, int y, int width, int height) {
        GLES30.nglInvalidateSubFramebuffer(target, numAttachments, bufferAddress(attachments), x, y, width, height);
    }
}
