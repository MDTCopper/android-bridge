package copper.bridge.v1;

import arc.graphics.*;
import java.nio.*;
import org.lwjgl.*;
import org.lwjgl.opengles.*;
import org.lwjgl.system.*;

/**
 * JVM-side implementation of arc's OpenGL ES 3.0 interface, layered on top of {@link BridgeGL20}.
 *
 * <p>Only the ES 3.0 additions live here; the reason every call has to travel through LWJGL's
 * OpenGL ES bindings is the one given there.</p>
 */
public class BridgeGL30 extends BridgeGL20 implements GL30 {

    @Override
    public void glReadBuffer(int mode) {
        GLES30.glReadBuffer(mode);
    }

    @Override
    public void glDrawRangeElements(int mode, int start, int end, int count, int type, Buffer indices) {
        GLES30.glDrawRangeElements(mode, start, end, count, type, bufferAddress(indices));
    }

    @Override
    public void glDrawRangeElements(int mode, int start, int end, int count, int type, int offset) {
        GLES30.glDrawRangeElements(mode, start, end, count, type, (long)offset);
    }

    @Override
    public void glTexImage3D(int target, int level, int internalformat, int width, int height, int depth, int border, int format, int type, Buffer pixels) {
        GLES30.glTexImage3D(target, level, internalformat, width, height, depth, border, format, type, bufferAddress(pixels));
    }

    @Override
    public void glTexImage3D(int target, int level, int internalformat, int width, int height, int depth, int border, int format, int type, int offset) {
        GLES30.glTexImage3D(target, level, internalformat, width, height, depth, border, format, type, (long)offset);
    }

    @Override
    public void glTexSubImage3D(int target, int level, int xoffset, int yoffset, int zoffset, int width, int height, int depth, int format, int type, Buffer pixels) {
        GLES30.glTexSubImage3D(target, level, xoffset, yoffset, zoffset, width, height, depth, format, type, bufferAddress(pixels));
    }

    @Override
    public void glTexSubImage3D(int target, int level, int xoffset, int yoffset, int zoffset, int width, int height, int depth, int format, int type, int offset) {
        GLES30.glTexSubImage3D(target, level, xoffset, yoffset, zoffset, width, height, depth, format, type, (long)offset);
    }

    @Override
    public void glCopyTexSubImage3D(int target, int level, int xoffset, int yoffset, int zoffset, int x, int y, int width, int height) {
        GLES30.glCopyTexSubImage3D(target, level, xoffset, yoffset, zoffset, x, y, width, height);
    }

    @Override
    public void glGenQueries(int n, IntBuffer ids) {
        GLES30.nglGenQueries(n, bufferAddress(ids));
    }

    @Override
    public void glDeleteQueries(int n, IntBuffer ids) {
        GLES30.nglDeleteQueries(n, bufferAddress(ids));
    }

    @Override
    public boolean glIsQuery(int id) {
        return GLES30.glIsQuery(id);
    }

    @Override
    public void glBeginQuery(int target, int id) {
        GLES30.glBeginQuery(target, id);
    }

    @Override
    public void glEndQuery(int target) {
        GLES30.glEndQuery(target);
    }

    @Override
    public void glGetQueryiv(int target, int pname, IntBuffer params) {
        GLES30.glGetQueryiv(target, pname, params);
    }

    @Override
    public void glGetQueryObjectuiv(int id, int pname, IntBuffer params) {
        GLES30.glGetQueryObjectuiv(id, pname, params);
    }

    @Override
    public boolean glUnmapBuffer(int target) {
        return GLES30.glUnmapBuffer(target);
    }

    @Override
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

    @Override
    public void glDrawBuffers(int n, IntBuffer bufs) {
        GLES30.nglDrawBuffers(n, bufferAddress(bufs));
    }

    @Override
    public void glUniformMatrix2x3fv(int location, int count, boolean transpose, FloatBuffer value) {
        GLES30.nglUniformMatrix2x3fv(location, count, transpose, bufferAddress(value));
    }

    @Override
    public void glUniformMatrix3x2fv(int location, int count, boolean transpose, FloatBuffer value) {
        GLES30.nglUniformMatrix3x2fv(location, count, transpose, bufferAddress(value));
    }

    @Override
    public void glUniformMatrix2x4fv(int location, int count, boolean transpose, FloatBuffer value) {
        GLES30.nglUniformMatrix2x4fv(location, count, transpose, bufferAddress(value));
    }

    @Override
    public void glUniformMatrix4x2fv(int location, int count, boolean transpose, FloatBuffer value) {
        GLES30.nglUniformMatrix4x2fv(location, count, transpose, bufferAddress(value));
    }

    @Override
    public void glUniformMatrix3x4fv(int location, int count, boolean transpose, FloatBuffer value) {
        GLES30.nglUniformMatrix3x4fv(location, count, transpose, bufferAddress(value));
    }

    @Override
    public void glUniformMatrix4x3fv(int location, int count, boolean transpose, FloatBuffer value) {
        GLES30.nglUniformMatrix4x3fv(location, count, transpose, bufferAddress(value));
    }

    @Override
    public void glBlitFramebuffer(int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
        GLES30.glBlitFramebuffer(srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
    }

    @Override
    public void glRenderbufferStorageMultisample(int target, int samples, int internalformat, int width, int height) {
        GLES30.glRenderbufferStorageMultisample(target, samples, internalformat, width, height);
    }

    @Override
    public void glFramebufferTextureLayer(int target, int attachment, int texture, int level, int layer) {
        GLES30.glFramebufferTextureLayer(target, attachment, texture, level, layer);
    }

    @Override
    public void glFlushMappedBufferRange(int target, int offset, int length) {
        GLES30.glFlushMappedBufferRange(target, offset, length);
    }

    @Override
    public void glBindVertexArray(int array) {
        GLES30.glBindVertexArray(array);
    }

    @Override
    public void glDeleteVertexArrays(int n, IntBuffer arrays) {
        GLES30.nglDeleteVertexArrays(n, bufferAddress(arrays));
    }

    @Override
    public void glGenVertexArrays(int n, IntBuffer arrays) {
        GLES30.nglGenVertexArrays(n, bufferAddress(arrays));
    }

    @Override
    public boolean glIsVertexArray(int array) {
        return GLES30.glIsVertexArray(array);
    }

    @Override
    public void glBeginTransformFeedback(int primitiveMode) {
        GLES30.glBeginTransformFeedback(primitiveMode);
    }

    @Override
    public void glEndTransformFeedback() {
        GLES30.glEndTransformFeedback();
    }

    @Override
    public void glBindBufferRange(int target, int index, int buffer, int offset, int size) {
        GLES30.glBindBufferRange(target, index, buffer, offset, size);
    }

    @Override
    public void glBindBufferBase(int target, int index, int buffer) {
        GLES30.glBindBufferBase(target, index, buffer);
    }

    @Override
    public void glTransformFeedbackVaryings(int program, String[] varyings, int bufferMode) {
        GLES30.glTransformFeedbackVaryings(program, varyings, bufferMode);
    }

    @Override
    public void glVertexAttribIPointer(int index, int size, int type, int stride, int offset) {
        GLES30.glVertexAttribIPointer(index, size, type, stride, (long)offset);
    }

    @Override
    public void glGetVertexAttribIiv(int index, int pname, IntBuffer params) {
        GLES30.glGetVertexAttribIiv(index, pname, params);
    }

    @Override
    public void glGetVertexAttribIuiv(int index, int pname, IntBuffer params) {
        GLES30.glGetVertexAttribIuiv(index, pname, params);
    }

    @Override
    public void glVertexAttribI4i(int index, int x, int y, int z, int w) {
        GLES30.glVertexAttribI4i(index, x, y, z, w);
    }

    @Override
    public void glVertexAttribI4ui(int index, int x, int y, int z, int w) {
        GLES30.glVertexAttribI4ui(index, x, y, z, w);
    }

    @Override
    public void glGetUniformuiv(int program, int location, IntBuffer params) {
        GLES30.glGetUniformuiv(program, location, params);
    }

    @Override
    public int glGetFragDataLocation(int program, String name) {
        return GLES30.glGetFragDataLocation(program, name);
    }

    @Override
    public void glUniform1uiv(int location, int count, IntBuffer value) {
        GLES30.nglUniform1uiv(location, count, bufferAddress(value));
    }

    @Override
    public void glUniform3uiv(int location, int count, IntBuffer value) {
        GLES30.nglUniform3uiv(location, count, bufferAddress(value));
    }

    @Override
    public void glUniform4uiv(int location, int count, IntBuffer value) {
        GLES30.nglUniform4uiv(location, count, bufferAddress(value));
    }

    @Override
    public void glClearBufferiv(int buffer, int drawbuffer, IntBuffer value) {
        GLES30.glClearBufferiv(buffer, drawbuffer, value);
    }

    @Override
    public void glClearBufferuiv(int buffer, int drawbuffer, IntBuffer value) {
        GLES30.glClearBufferuiv(buffer, drawbuffer, value);
    }

    @Override
    public void glClearBufferfv(int buffer, int drawbuffer, FloatBuffer value) {
        GLES30.glClearBufferfv(buffer, drawbuffer, value);
    }

    @Override
    public void glClearBufferfi(int buffer, int drawbuffer, float depth, int stencil) {
        GLES30.glClearBufferfi(buffer, drawbuffer, depth, stencil);
    }

    @Override
    public String glGetStringi(int name, int index) {
        return GLES30.glGetStringi(name, index);
    }

    @Override
    public void glCopyBufferSubData(int readTarget, int writeTarget, int readOffset, int writeOffset, int size) {
        GLES30.glCopyBufferSubData(readTarget, writeTarget, readOffset, writeOffset, size);
    }

    @Override
    public void glGetUniformIndices(int program, String[] uniformNames, IntBuffer uniformIndices) {
        try(MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer names = stack.mallocPointer(uniformNames.length);
            for (int i = 0; i < uniformNames.length; i++) {
                names.put(i, stack.UTF8(uniformNames[i]));
            }
            GLES30.glGetUniformIndices(program, names, uniformIndices);
        }
    }

    @Override
    public void glGetActiveUniformsiv(int program, int uniformCount, IntBuffer uniformIndices, int pname, IntBuffer params) {
        GLES30.nglGetActiveUniformsiv(program, uniformCount, bufferAddress(uniformIndices), pname, bufferAddress(params));
    }

    @Override
    public int glGetUniformBlockIndex(int program, String uniformBlockName) {
        return GLES30.glGetUniformBlockIndex(program, uniformBlockName);
    }

    @Override
    public void glGetActiveUniformBlockiv(int program, int uniformBlockIndex, int pname, IntBuffer params) {
        GLES30.glGetActiveUniformBlockiv(program, uniformBlockIndex, pname, params);
    }

    @Override
    public void glGetActiveUniformBlockName(int program, int uniformBlockIndex, Buffer length, Buffer uniformBlockName) {
        if (!(uniformBlockName instanceof ByteBuffer)) {
            throw new IllegalArgumentException("Unsupported buffer type for a uniform block name: " + uniformBlockName.getClass().getName());
        }
        if (length != null && !(length instanceof IntBuffer)) {
            throw new IllegalArgumentException("Unsupported buffer type for a uniform block name length: " + length.getClass().getName());
        }
        GLES30.glGetActiveUniformBlockName(program, uniformBlockIndex, (IntBuffer)length, (ByteBuffer)uniformBlockName);
    }

    @Override
    public void glUniformBlockBinding(int program, int uniformBlockIndex, int uniformBlockBinding) {
        GLES30.glUniformBlockBinding(program, uniformBlockIndex, uniformBlockBinding);
    }

    @Override
    public void glDrawArraysInstanced(int mode, int first, int count, int instanceCount) {
        GLES30.glDrawArraysInstanced(mode, first, count, instanceCount);
    }

    @Override
    public void glDrawElementsInstanced(int mode, int count, int type, int indicesOffset, int instanceCount) {
        GLES30.glDrawElementsInstanced(mode, count, type, (long)indicesOffset, instanceCount);
    }

    @Override
    public void glGetInteger64v(int pname, LongBuffer params) {
        GLES30.glGetInteger64v(pname, params);
    }

    @Override
    public void glGetBufferParameteri64v(int target, int pname, LongBuffer params) {
        GLES30.glGetBufferParameteri64v(target, pname, params);
    }

    @Override
    public void glGenSamplers(int count, IntBuffer samplers) {
        GLES30.nglGenSamplers(count, bufferAddress(samplers));
    }

    @Override
    public void glDeleteSamplers(int count, IntBuffer samplers) {
        GLES30.nglDeleteSamplers(count, bufferAddress(samplers));
    }

    @Override
    public boolean glIsSampler(int sampler) {
        return GLES30.glIsSampler(sampler);
    }

    @Override
    public void glBindSampler(int unit, int sampler) {
        GLES30.glBindSampler(unit, sampler);
    }

    @Override
    public void glSamplerParameteri(int sampler, int pname, int param) {
        GLES30.glSamplerParameteri(sampler, pname, param);
    }

    @Override
    public void glSamplerParameteriv(int sampler, int pname, IntBuffer param) {
        GLES30.glSamplerParameteriv(sampler, pname, param);
    }

    @Override
    public void glSamplerParameterf(int sampler, int pname, float param) {
        GLES30.glSamplerParameterf(sampler, pname, param);
    }

    @Override
    public void glSamplerParameterfv(int sampler, int pname, FloatBuffer param) {
        GLES30.glSamplerParameterfv(sampler, pname, param);
    }

    @Override
    public void glGetSamplerParameteriv(int sampler, int pname, IntBuffer params) {
        GLES30.glGetSamplerParameteriv(sampler, pname, params);
    }

    @Override
    public void glGetSamplerParameterfv(int sampler, int pname, FloatBuffer params) {
        GLES30.glGetSamplerParameterfv(sampler, pname, params);
    }

    @Override
    public void glVertexAttribDivisor(int index, int divisor) {
        GLES30.glVertexAttribDivisor(index, divisor);
    }

    @Override
    public void glBindTransformFeedback(int target, int id) {
        GLES30.glBindTransformFeedback(target, id);
    }

    @Override
    public void glDeleteTransformFeedbacks(int n, IntBuffer ids) {
        GLES30.nglDeleteTransformFeedbacks(n, bufferAddress(ids));
    }

    @Override
    public void glGenTransformFeedbacks(int n, IntBuffer ids) {
        GLES30.nglGenTransformFeedbacks(n, bufferAddress(ids));
    }

    @Override
    public boolean glIsTransformFeedback(int id) {
        return GLES30.glIsTransformFeedback(id);
    }

    @Override
    public void glPauseTransformFeedback() {
        GLES30.glPauseTransformFeedback();
    }

    @Override
    public void glResumeTransformFeedback() {
        GLES30.glResumeTransformFeedback();
    }

    @Override
    public void glProgramParameteri(int program, int pname, int value) {
        GLES30.glProgramParameteri(program, pname, value);
    }

    @Override
    public void glInvalidateFramebuffer(int target, int numAttachments, IntBuffer attachments) {
        GLES30.nglInvalidateFramebuffer(target, numAttachments, bufferAddress(attachments));
    }

    @Override
    public void glInvalidateSubFramebuffer(int target, int numAttachments, IntBuffer attachments, int x, int y, int width, int height) {
        GLES30.nglInvalidateSubFramebuffer(target, numAttachments, bufferAddress(attachments), x, y, width, height);
    }
}
