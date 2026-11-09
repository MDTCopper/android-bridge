package copper.bridge.v5;

import copper.bridge.annotation.BridgeOverride;

import io.anuke.arc.graphics.*;
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

    @BridgeOverride
    public void glReadBuffer(int mode) {
        GLES30.glReadBuffer(mode);
    }

    @BridgeOverride
    public void glDrawRangeElements(int mode, int start, int end, int count, int type, Buffer indices) {
        GLES30.glDrawRangeElements(mode, start, end, count, type, bufferAddress(indices));
    }

    @BridgeOverride
    public void glDrawRangeElements(int mode, int start, int end, int count, int type, int offset) {
        GLES30.glDrawRangeElements(mode, start, end, count, type, (long)offset);
    }

    @BridgeOverride
    public void glTexImage3D(int target, int level, int internalformat, int width, int height, int depth, int border, int format, int type, Buffer pixels) {
        GLES30.glTexImage3D(target, level, internalformat, width, height, depth, border, format, type, bufferAddress(pixels));
    }

    @BridgeOverride
    public void glTexImage3D(int target, int level, int internalformat, int width, int height, int depth, int border, int format, int type, int offset) {
        GLES30.glTexImage3D(target, level, internalformat, width, height, depth, border, format, type, (long)offset);
    }

    @BridgeOverride
    public void glTexSubImage3D(int target, int level, int xoffset, int yoffset, int zoffset, int width, int height, int depth, int format, int type, Buffer pixels) {
        GLES30.glTexSubImage3D(target, level, xoffset, yoffset, zoffset, width, height, depth, format, type, bufferAddress(pixels));
    }

    @BridgeOverride
    public void glTexSubImage3D(int target, int level, int xoffset, int yoffset, int zoffset, int width, int height, int depth, int format, int type, int offset) {
        GLES30.glTexSubImage3D(target, level, xoffset, yoffset, zoffset, width, height, depth, format, type, (long)offset);
    }

    @BridgeOverride
    public void glCopyTexSubImage3D(int target, int level, int xoffset, int yoffset, int zoffset, int x, int y, int width, int height) {
        GLES30.glCopyTexSubImage3D(target, level, xoffset, yoffset, zoffset, x, y, width, height);
    }

    @BridgeOverride
    public void glGenQueries(int n, IntBuffer ids) {
        GLES30.nglGenQueries(n, bufferAddress(ids));
    }

    /** Array overload of the older GL30 interface; gone again on the newer end, so no {@code @BridgeOverride}. */
    @BridgeOverride
    public void glGenQueries(int n, int[] ids, int offset) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer buffer = stack.mallocInt(n);
            buffer.position(0);
            GLES30.nglGenQueries(n, bufferAddress(buffer));
            buffer.get(ids, offset, n);
        }
    }

    @BridgeOverride
    public void glDeleteQueries(int n, IntBuffer ids) {
        GLES30.nglDeleteQueries(n, bufferAddress(ids));
    }

    /** Array overload of the older GL30 interface; gone again on the newer end, so no {@code @BridgeOverride}. */
    @BridgeOverride
    public void glDeleteQueries(int n, int[] ids, int offset) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer buffer = stack.mallocInt(n);
            buffer.put(ids, offset, n);
            buffer.position(0);
            GLES30.nglDeleteQueries(n, bufferAddress(buffer));
        }
    }

    @BridgeOverride
    public boolean glIsQuery(int id) {
        return GLES30.glIsQuery(id);
    }

    @BridgeOverride
    public void glBeginQuery(int target, int id) {
        GLES30.glBeginQuery(target, id);
    }

    @BridgeOverride
    public void glEndQuery(int target) {
        GLES30.glEndQuery(target);
    }

    @BridgeOverride
    public void glGetQueryiv(int target, int pname, IntBuffer params) {
        GLES30.glGetQueryiv(target, pname, params);
    }

    @BridgeOverride
    public void glGetQueryObjectuiv(int id, int pname, IntBuffer params) {
        GLES30.glGetQueryObjectuiv(id, pname, params);
    }

    @BridgeOverride
    public boolean glUnmapBuffer(int target) {
        return GLES30.glUnmapBuffer(target);
    }

    @BridgeOverride
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

    @BridgeOverride
    public void glDrawBuffers(int n, IntBuffer bufs) {
        GLES30.nglDrawBuffers(n, bufferAddress(bufs));
    }

    @BridgeOverride
    public void glUniformMatrix2x3fv(int location, int count, boolean transpose, FloatBuffer value) {
        GLES30.nglUniformMatrix2x3fv(location, count, transpose, bufferAddress(value));
    }

    @BridgeOverride
    public void glUniformMatrix3x2fv(int location, int count, boolean transpose, FloatBuffer value) {
        GLES30.nglUniformMatrix3x2fv(location, count, transpose, bufferAddress(value));
    }

    @BridgeOverride
    public void glUniformMatrix2x4fv(int location, int count, boolean transpose, FloatBuffer value) {
        GLES30.nglUniformMatrix2x4fv(location, count, transpose, bufferAddress(value));
    }

    @BridgeOverride
    public void glUniformMatrix4x2fv(int location, int count, boolean transpose, FloatBuffer value) {
        GLES30.nglUniformMatrix4x2fv(location, count, transpose, bufferAddress(value));
    }

    @BridgeOverride
    public void glUniformMatrix3x4fv(int location, int count, boolean transpose, FloatBuffer value) {
        GLES30.nglUniformMatrix3x4fv(location, count, transpose, bufferAddress(value));
    }

    @BridgeOverride
    public void glUniformMatrix4x3fv(int location, int count, boolean transpose, FloatBuffer value) {
        GLES30.nglUniformMatrix4x3fv(location, count, transpose, bufferAddress(value));
    }

    @BridgeOverride
    public void glBlitFramebuffer(int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
        GLES30.glBlitFramebuffer(srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
    }

    @BridgeOverride
    public void glRenderbufferStorageMultisample(int target, int samples, int internalformat, int width, int height) {
        GLES30.glRenderbufferStorageMultisample(target, samples, internalformat, width, height);
    }

    @BridgeOverride
    public void glFramebufferTextureLayer(int target, int attachment, int texture, int level, int layer) {
        GLES30.glFramebufferTextureLayer(target, attachment, texture, level, layer);
    }

    @BridgeOverride
    public void glFlushMappedBufferRange(int target, int offset, int length) {
        GLES30.glFlushMappedBufferRange(target, offset, length);
    }

    @BridgeOverride
    public void glBindVertexArray(int array) {
        GLES30.glBindVertexArray(array);
    }

    @BridgeOverride
    public void glDeleteVertexArrays(int n, IntBuffer arrays) {
        GLES30.nglDeleteVertexArrays(n, bufferAddress(arrays));
    }

    /** Array overload of the older GL30 interface; gone again on the newer end, so no {@code @BridgeOverride}. */
    @BridgeOverride
    public void glDeleteVertexArrays(int n, int[] arrays, int offset) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer buffer = stack.mallocInt(n);
            buffer.put(arrays, offset, n);
            buffer.position(0);
            GLES30.nglDeleteVertexArrays(n, bufferAddress(buffer));
        }
    }

    @BridgeOverride
    public void glGenVertexArrays(int n, IntBuffer arrays) {
        GLES30.nglGenVertexArrays(n, bufferAddress(arrays));
    }

    /** Array overload of the older GL30 interface; gone again on the newer end, so no {@code @BridgeOverride}. */
    @BridgeOverride
    public void glGenVertexArrays(int n, int[] arrays, int offset) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer buffer = stack.mallocInt(n);
            buffer.position(0);
            GLES30.nglGenVertexArrays(n, bufferAddress(buffer));
            buffer.get(arrays, offset, n);
        }
    }

    @BridgeOverride
    public boolean glIsVertexArray(int array) {
        return GLES30.glIsVertexArray(array);
    }

    @BridgeOverride
    public void glBeginTransformFeedback(int primitiveMode) {
        GLES30.glBeginTransformFeedback(primitiveMode);
    }

    @BridgeOverride
    public void glEndTransformFeedback() {
        GLES30.glEndTransformFeedback();
    }

    @BridgeOverride
    public void glBindBufferRange(int target, int index, int buffer, int offset, int size) {
        GLES30.glBindBufferRange(target, index, buffer, offset, size);
    }

    @BridgeOverride
    public void glBindBufferBase(int target, int index, int buffer) {
        GLES30.glBindBufferBase(target, index, buffer);
    }

    @BridgeOverride
    public void glTransformFeedbackVaryings(int program, String[] varyings, int bufferMode) {
        GLES30.glTransformFeedbackVaryings(program, varyings, bufferMode);
    }

    @BridgeOverride
    public void glVertexAttribIPointer(int index, int size, int type, int stride, int offset) {
        GLES30.glVertexAttribIPointer(index, size, type, stride, (long)offset);
    }

    @BridgeOverride
    public void glGetVertexAttribIiv(int index, int pname, IntBuffer params) {
        GLES30.glGetVertexAttribIiv(index, pname, params);
    }

    @BridgeOverride
    public void glGetVertexAttribIuiv(int index, int pname, IntBuffer params) {
        GLES30.glGetVertexAttribIuiv(index, pname, params);
    }

    @BridgeOverride
    public void glVertexAttribI4i(int index, int x, int y, int z, int w) {
        GLES30.glVertexAttribI4i(index, x, y, z, w);
    }

    @BridgeOverride
    public void glVertexAttribI4ui(int index, int x, int y, int z, int w) {
        GLES30.glVertexAttribI4ui(index, x, y, z, w);
    }

    @BridgeOverride
    public void glGetUniformuiv(int program, int location, IntBuffer params) {
        GLES30.glGetUniformuiv(program, location, params);
    }

    @BridgeOverride
    public int glGetFragDataLocation(int program, String name) {
        return GLES30.glGetFragDataLocation(program, name);
    }

    @BridgeOverride
    public void glUniform1uiv(int location, int count, IntBuffer value) {
        GLES30.nglUniform1uiv(location, count, bufferAddress(value));
    }

    @BridgeOverride
    public void glUniform3uiv(int location, int count, IntBuffer value) {
        GLES30.nglUniform3uiv(location, count, bufferAddress(value));
    }

    @BridgeOverride
    public void glUniform4uiv(int location, int count, IntBuffer value) {
        GLES30.nglUniform4uiv(location, count, bufferAddress(value));
    }

    @BridgeOverride
    public void glClearBufferiv(int buffer, int drawbuffer, IntBuffer value) {
        GLES30.glClearBufferiv(buffer, drawbuffer, value);
    }

    @BridgeOverride
    public void glClearBufferuiv(int buffer, int drawbuffer, IntBuffer value) {
        GLES30.glClearBufferuiv(buffer, drawbuffer, value);
    }

    @BridgeOverride
    public void glClearBufferfv(int buffer, int drawbuffer, FloatBuffer value) {
        GLES30.glClearBufferfv(buffer, drawbuffer, value);
    }

    @BridgeOverride
    public void glClearBufferfi(int buffer, int drawbuffer, float depth, int stencil) {
        GLES30.glClearBufferfi(buffer, drawbuffer, depth, stencil);
    }

    @BridgeOverride
    public String glGetStringi(int name, int index) {
        return GLES30.glGetStringi(name, index);
    }

    @BridgeOverride
    public void glCopyBufferSubData(int readTarget, int writeTarget, int readOffset, int writeOffset, int size) {
        GLES30.glCopyBufferSubData(readTarget, writeTarget, readOffset, writeOffset, size);
    }

    @BridgeOverride
    public void glGetUniformIndices(int program, String[] uniformNames, IntBuffer uniformIndices) {
        try(MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer names = stack.mallocPointer(uniformNames.length);
            for (int i = 0; i < uniformNames.length; i++) {
                names.put(i, stack.UTF8(uniformNames[i]));
            }
            GLES30.glGetUniformIndices(program, names, uniformIndices);
        }
    }

    @BridgeOverride
    public void glGetActiveUniformsiv(int program, int uniformCount, IntBuffer uniformIndices, int pname, IntBuffer params) {
        GLES30.nglGetActiveUniformsiv(program, uniformCount, bufferAddress(uniformIndices), pname, bufferAddress(params));
    }

    @BridgeOverride
    public int glGetUniformBlockIndex(int program, String uniformBlockName) {
        return GLES30.glGetUniformBlockIndex(program, uniformBlockName);
    }

    @BridgeOverride
    public void glGetActiveUniformBlockiv(int program, int uniformBlockIndex, int pname, IntBuffer params) {
        GLES30.glGetActiveUniformBlockiv(program, uniformBlockIndex, pname, params);
    }

    /** String overload of the older GL30 interface; gone again on the newer end, so it exists at one end only. */
    @BridgeOverride
    public String glGetActiveUniformBlockName(int program, int uniformBlockIndex) {
        return GLES30.glGetActiveUniformBlockName(program, uniformBlockIndex);
    }

    @BridgeOverride
    public void glGetActiveUniformBlockName(int program, int uniformBlockIndex, Buffer length, Buffer uniformBlockName) {        if (!(uniformBlockName instanceof ByteBuffer)) {
            throw new IllegalArgumentException("Unsupported buffer type for a uniform block name: " + uniformBlockName.getClass().getName());
        }
        if (length != null && !(length instanceof IntBuffer)) {
            throw new IllegalArgumentException("Unsupported buffer type for a uniform block name length: " + length.getClass().getName());
        }
        GLES30.glGetActiveUniformBlockName(program, uniformBlockIndex, (IntBuffer)length, (ByteBuffer)uniformBlockName);
    }

    @BridgeOverride
    public void glUniformBlockBinding(int program, int uniformBlockIndex, int uniformBlockBinding) {
        GLES30.glUniformBlockBinding(program, uniformBlockIndex, uniformBlockBinding);
    }

    @BridgeOverride
    public void glDrawArraysInstanced(int mode, int first, int count, int instanceCount) {
        GLES30.glDrawArraysInstanced(mode, first, count, instanceCount);
    }

    @BridgeOverride
    public void glDrawElementsInstanced(int mode, int count, int type, int indicesOffset, int instanceCount) {
        GLES30.glDrawElementsInstanced(mode, count, type, (long)indicesOffset, instanceCount);
    }

    @BridgeOverride
    public void glGetInteger64v(int pname, LongBuffer params) {
        GLES30.glGetInteger64v(pname, params);
    }

    @BridgeOverride
    public void glGetBufferParameteri64v(int target, int pname, LongBuffer params) {
        GLES30.glGetBufferParameteri64v(target, pname, params);
    }

    @BridgeOverride
    public void glGenSamplers(int count, IntBuffer samplers) {
        GLES30.nglGenSamplers(count, bufferAddress(samplers));
    }

    /** Array overload of the older GL30 interface; gone again on the newer end, so no {@code @BridgeOverride}. */
    @BridgeOverride
    public void glGenSamplers(int count, int[] samplers, int offset) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer buffer = stack.mallocInt(count);
            buffer.position(0);
            GLES30.nglGenSamplers(count, bufferAddress(buffer));
            buffer.get(samplers, offset, count);
        }
    }

    @BridgeOverride
    public void glDeleteSamplers(int count, IntBuffer samplers) {
        GLES30.nglDeleteSamplers(count, bufferAddress(samplers));
    }

    /** Array overload of the older GL30 interface; gone again on the newer end, so no {@code @BridgeOverride}. */
    @BridgeOverride
    public void glDeleteSamplers(int count, int[] samplers, int offset) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer buffer = stack.mallocInt(count);
            buffer.put(samplers, offset, count);
            buffer.position(0);
            GLES30.nglDeleteSamplers(count, bufferAddress(buffer));
        }
    }

    @BridgeOverride
    public boolean glIsSampler(int sampler) {
        return GLES30.glIsSampler(sampler);
    }

    @BridgeOverride
    public void glBindSampler(int unit, int sampler) {
        GLES30.glBindSampler(unit, sampler);
    }

    @BridgeOverride
    public void glSamplerParameteri(int sampler, int pname, int param) {
        GLES30.glSamplerParameteri(sampler, pname, param);
    }

    @BridgeOverride
    public void glSamplerParameteriv(int sampler, int pname, IntBuffer param) {
        GLES30.glSamplerParameteriv(sampler, pname, param);
    }

    @BridgeOverride
    public void glSamplerParameterf(int sampler, int pname, float param) {
        GLES30.glSamplerParameterf(sampler, pname, param);
    }

    @BridgeOverride
    public void glSamplerParameterfv(int sampler, int pname, FloatBuffer param) {
        GLES30.glSamplerParameterfv(sampler, pname, param);
    }

    @BridgeOverride
    public void glGetSamplerParameteriv(int sampler, int pname, IntBuffer params) {
        GLES30.glGetSamplerParameteriv(sampler, pname, params);
    }

    @BridgeOverride
    public void glGetSamplerParameterfv(int sampler, int pname, FloatBuffer params) {
        GLES30.glGetSamplerParameterfv(sampler, pname, params);
    }

    @BridgeOverride
    public void glVertexAttribDivisor(int index, int divisor) {
        GLES30.glVertexAttribDivisor(index, divisor);
    }

    @BridgeOverride
    public void glBindTransformFeedback(int target, int id) {
        GLES30.glBindTransformFeedback(target, id);
    }

    @BridgeOverride
    public void glDeleteTransformFeedbacks(int n, IntBuffer ids) {
        GLES30.nglDeleteTransformFeedbacks(n, bufferAddress(ids));
    }

    /** Array overload of the older GL30 interface; gone again on the newer end, so no {@code @BridgeOverride}. */
    @BridgeOverride
    public void glDeleteTransformFeedbacks(int n, int[] ids, int offset) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer buffer = stack.mallocInt(n);
            buffer.put(ids, offset, n);
            buffer.position(0);
            GLES30.nglDeleteTransformFeedbacks(n, bufferAddress(buffer));
        }
    }

    @BridgeOverride
    public void glGenTransformFeedbacks(int n, IntBuffer ids) {
        GLES30.nglGenTransformFeedbacks(n, bufferAddress(ids));
    }

    /** Array overload of the older GL30 interface; gone again on the newer end, so no {@code @BridgeOverride}. */
    @BridgeOverride
    public void glGenTransformFeedbacks(int n, int[] ids, int offset) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer buffer = stack.mallocInt(n);
            buffer.position(0);
            GLES30.nglGenTransformFeedbacks(n, bufferAddress(buffer));
            buffer.get(ids, offset, n);
        }
    }

    @BridgeOverride
    public boolean glIsTransformFeedback(int id) {
        return GLES30.glIsTransformFeedback(id);
    }

    @BridgeOverride
    public void glPauseTransformFeedback() {
        GLES30.glPauseTransformFeedback();
    }

    @BridgeOverride
    public void glResumeTransformFeedback() {
        GLES30.glResumeTransformFeedback();
    }

    @BridgeOverride
    public void glProgramParameteri(int program, int pname, int value) {
        GLES30.glProgramParameteri(program, pname, value);
    }

    @BridgeOverride
    public void glInvalidateFramebuffer(int target, int numAttachments, IntBuffer attachments) {
        GLES30.nglInvalidateFramebuffer(target, numAttachments, bufferAddress(attachments));
    }

    @BridgeOverride
    public void glInvalidateSubFramebuffer(int target, int numAttachments, IntBuffer attachments, int x, int y, int width, int height) {
        GLES30.nglInvalidateSubFramebuffer(target, numAttachments, bufferAddress(attachments), x, y, width, height);
    }
}