package copper.bridge.v9;

import copper.bridge.annotation.BridgeInterface;

import arc.graphics.*;
import arc.graphics.gl.*;

import java.nio.*;
import org.lwjgl.*;
import org.lwjgl.opengles.*;
import org.lwjgl.system.*;

/**
 * JVM-side implementation of arc's {@link GLProvider}, the one GL entry point v9's arc reaches the driver
 * through. {@code android.opengl} is unreachable in this JVM, so LWJGL's OpenGL ES bindings are the only route
 * to GL; where v9's own android backend hands buffers to {@code GLES20} directly, these calls take addresses,
 * hence the helper below.
 *
 * <p>One class, like that backend: the ES2 and ES3 halves of the interface live together instead of in a base
 * class and a subclass, because v9's arc no longer has a separate ES3 layer to install on a capable device.
 */
public class BridgeGLProvider implements GLProvider {

    /** The native address of a client buffer: LWJGL takes a pointer where {@code android.opengl} takes the buffer. */
    private static long bufferAddress(Buffer buffer) {
        if (buffer == null) {
            return 0L;
        }
        if (buffer instanceof ByteBuffer || buffer instanceof ShortBuffer || buffer instanceof IntBuffer || buffer instanceof FloatBuffer) {
            return MemoryUtil.memAddress(buffer);
        }
        throw new IllegalArgumentException("Unsupported buffer type for GL data: " + buffer.getClass().getName());
    }

    // The OpenGL ES 2.0 half of the interface.

    @BridgeInterface
    public void glActiveTexture(int texture) {
        GLES20.glActiveTexture(texture);
    }

    @BridgeInterface
    public void glAttachShader(int program, int shader) {
        GLES20.glAttachShader(program, shader);
    }

    @BridgeInterface
    public void glBindAttribLocation(int program, int index, String name) {
        GLES20.glBindAttribLocation(program, index, name);
    }

    @BridgeInterface
    public void glBindBuffer(int target, int buffer) {
        GLES20.glBindBuffer(target, buffer);
    }

    @BridgeInterface
    public void glBindFramebuffer(int target, int framebuffer) {
        GLES20.glBindFramebuffer(target, framebuffer);
    }

    @BridgeInterface
    public void glBindRenderbuffer(int target, int renderbuffer) {
        GLES20.glBindRenderbuffer(target, renderbuffer);
    }

    @BridgeInterface
    public void glBindTexture(int target, int texture) {
        GLES20.glBindTexture(target, texture);
    }

    @BridgeInterface
    public void glBlendColor(float red, float green, float blue, float alpha) {
        GLES20.glBlendColor(red, green, blue, alpha);
    }

    @BridgeInterface
    public void glBlendEquation(int mode) {
        GLES20.glBlendEquation(mode);
    }

    @BridgeInterface
    public void glBlendEquationSeparate(int modeRGB, int modeAlpha) {
        GLES20.glBlendEquationSeparate(modeRGB, modeAlpha);
    }

    @BridgeInterface
    public void glBlendFunc(int sfactor, int dfactor) {
        GLES20.glBlendFunc(sfactor, dfactor);
    }

    @BridgeInterface
    public void glBlendFuncSeparate(int srcRGB, int dstRGB, int srcAlpha, int dstAlpha) {
        GLES20.glBlendFuncSeparate(srcRGB, dstRGB, srcAlpha, dstAlpha);
    }

    @BridgeInterface
    public void glBufferData(int target, int size, Buffer data, int usage) {
        if (data == null) {
            GLES20.glBufferData(target, size, usage);
        } else {
            GLES20.nglBufferData(target, size, bufferAddress(data), usage);
        }
    }

    @BridgeInterface
    public void glBufferSubData(int target, int offset, int size, Buffer data) {
        GLES20.nglBufferSubData(target, offset, size, bufferAddress(data));
    }

    @BridgeInterface
    public int glCheckFramebufferStatus(int target) {
        return GLES20.glCheckFramebufferStatus(target);
    }

    @BridgeInterface
    public void glClear(int mask) {
        GLES20.glClear(mask);
    }

    @BridgeInterface
    public void glClearColor(float red, float green, float blue, float alpha) {
        GLES20.glClearColor(red, green, blue, alpha);
    }

    @BridgeInterface
    public void glClearDepthf(float depth) {
        GLES20.glClearDepthf(depth);
    }

    @BridgeInterface
    public void glClearStencil(int s) {
        GLES20.glClearStencil(s);
    }

    @BridgeInterface
    public void glColorMask(boolean red, boolean green, boolean blue, boolean alpha) {
        GLES20.glColorMask(red, green, blue, alpha);
    }

    @BridgeInterface
    public void glCompileShader(int shader) {
        GLES20.glCompileShader(shader);
    }

    @BridgeInterface
    public void glCompressedTexImage2D(int target, int level, int internalformat, int width, int height, int border, int imageSize, Buffer data) {
        GLES20.glCompressedTexImage2D(target, level, internalformat, width, height, border, imageSize, bufferAddress(data));
    }

    @BridgeInterface
    public void glCompressedTexSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format, int imageSize, Buffer data) {
        GLES20.glCompressedTexSubImage2D(target, level, xoffset, yoffset, width, height, format, imageSize, bufferAddress(data));
    }

    @BridgeInterface
    public void glCopyTexImage2D(int target, int level, int internalformat, int x, int y, int width, int height, int border) {
        GLES20.glCopyTexImage2D(target, level, internalformat, x, y, width, height, border);
    }

    @BridgeInterface
    public void glCopyTexSubImage2D(int target, int level, int xoffset, int yoffset, int x, int y, int width, int height) {
        GLES20.glCopyTexSubImage2D(target, level, xoffset, yoffset, x, y, width, height);
    }

    @BridgeInterface
    public int glCreateProgram() {
        return GLES20.glCreateProgram();
    }

    @BridgeInterface
    public int glCreateShader(int type) {
        return GLES20.glCreateShader(type);
    }

    @BridgeInterface
    public void glCullFace(int mode) {
        GLES20.glCullFace(mode);
    }

    @BridgeInterface
    public void glDeleteBuffer(int buffer) {
        GLES20.glDeleteBuffers(buffer);
    }

    @BridgeInterface
    public void glDeleteFramebuffer(int framebuffer) {
        GLES20.glDeleteFramebuffers(framebuffer);
    }

    @BridgeInterface
    public void glDeleteProgram(int program) {
        GLES20.glDeleteProgram(program);
    }

    @BridgeInterface
    public void glDeleteRenderbuffer(int renderbuffer) {
        GLES20.glDeleteRenderbuffers(renderbuffer);
    }

    @BridgeInterface
    public void glDeleteShader(int shader) {
        GLES20.glDeleteShader(shader);
    }

    @BridgeInterface
    public void glDeleteTexture(int texture) {
        GLES20.glDeleteTextures(texture);
    }

    @BridgeInterface
    public void glDepthFunc(int func) {
        GLES20.glDepthFunc(func);
    }

    @BridgeInterface
    public void glDepthMask(boolean flag) {
        GLES20.glDepthMask(flag);
    }

    @BridgeInterface
    public void glDepthRangef(float zNear, float zFar) {
        GLES20.glDepthRangef(zNear, zFar);
    }

    @BridgeInterface
    public void glDetachShader(int program, int shader) {
        GLES20.glDetachShader(program, shader);
    }

    @BridgeInterface
    public void glDisable(int cap) {
        GLES20.glDisable(cap);
    }

    @BridgeInterface
    public void glDisableVertexAttribArray(int index) {
        GLES20.glDisableVertexAttribArray(index);
    }

    @BridgeInterface
    public void glDrawArrays(int mode, int first, int count) {
        GLES20.glDrawArrays(mode, first, count);
    }

    @BridgeInterface
    public void glDrawElements(int mode, int count, int type, Buffer indices) {
        GLES20.glDrawElements(mode, count, type, bufferAddress(indices));
    }

    @BridgeInterface
    public void glDrawElements(int mode, int count, int type, int indices) {
        GLES20.glDrawElements(mode, count, type, indices);
    }

    @BridgeInterface
    public void glEnable(int cap) {
        GLES20.glEnable(cap);
    }

    @BridgeInterface
    public void glEnableVertexAttribArray(int index) {
        GLES20.glEnableVertexAttribArray(index);
    }

    @BridgeInterface
    public void glFinish() {
        GLES20.glFinish();
    }

    @BridgeInterface
    public void glFlush() {
        GLES20.glFlush();
    }

    @BridgeInterface
    public void glFramebufferRenderbuffer(int target, int attachment, int renderbuffertarget, int renderbuffer) {
        GLES20.glFramebufferRenderbuffer(target, attachment, renderbuffertarget, renderbuffer);
    }

    @BridgeInterface
    public void glFramebufferTexture2D(int target, int attachment, int textarget, int texture, int level) {
        GLES20.glFramebufferTexture2D(target, attachment, textarget, texture, level);
    }

    @BridgeInterface
    public void glFrontFace(int mode) {
        GLES20.glFrontFace(mode);
    }

    @BridgeInterface
    public int glGenBuffer() {
        return GLES20.glGenBuffers();
    }

    @BridgeInterface
    public void glGenerateMipmap(int target) {
        GLES20.glGenerateMipmap(target);
    }

    @BridgeInterface
    public int glGenFramebuffer() {
        return GLES20.glGenFramebuffers();
    }

    @BridgeInterface
    public int glGenRenderbuffer() {
        return GLES20.glGenRenderbuffers();
    }

    @BridgeInterface
    public int glGenTexture() {
        return GLES20.glGenTextures();
    }

    @BridgeInterface
    public String glGetActiveAttrib(int program, int index, IntBuffer size, IntBuffer type) {
        return GLES20.glGetActiveAttrib(program, index, size, type);
    }

    @BridgeInterface
    public String glGetActiveUniform(int program, int index, IntBuffer size, IntBuffer type) {
        return GLES20.glGetActiveUniform(program, index, size, type);
    }

    @BridgeInterface
    public int glGetAttribLocation(int program, String name) {
        return GLES20.glGetAttribLocation(program, name);
    }

    @BridgeInterface
    public void glGetBooleanv(int pname, Buffer params) {
        if (params instanceof ByteBuffer) {
            GLES20.glGetBooleanv(pname, (ByteBuffer)params);
            return;
        }
        if (params instanceof IntBuffer) {
            IntBuffer destination = (IntBuffer)params;
            int start = destination.position();
            int count = destination.remaining();
            try(MemoryStack stack = MemoryStack.stackPush()) {
                ByteBuffer scratch = stack.malloc(count);
                GLES20.glGetBooleanv(pname, scratch);
                for (int i = 0; i < count; i++) {
                    destination.put(start + i, scratch.get(i) == 0 ? 0 : 1);
                }
            }
            return;
        }
        throw new IllegalArgumentException("Unsupported buffer type for boolean GL results: " + params.getClass().getName());
    }

    @BridgeInterface
    public void glGetBufferParameteriv(int target, int pname, IntBuffer params) {
        GLES20.glGetBufferParameteriv(target, pname, params);
    }

    @BridgeInterface
    public int glGetError() {
        return GLES20.glGetError();
    }

    @BridgeInterface
    public void glGetFloatv(int pname, FloatBuffer params) {
        GLES20.glGetFloatv(pname, params);
    }

    @BridgeInterface
    public void glGetFramebufferAttachmentParameteriv(int target, int attachment, int pname, IntBuffer params) {
        GLES20.glGetFramebufferAttachmentParameteriv(target, attachment, pname, params);
    }

    @BridgeInterface
    public void glGetIntegerv(int pname, IntBuffer params) {
        GLES20.glGetIntegerv(pname, params);
    }

    @BridgeInterface
    public String glGetProgramInfoLog(int program) {
        return GLES20.glGetProgramInfoLog(program);
    }

    @BridgeInterface
    public void glGetProgramiv(int program, int pname, IntBuffer params) {
        GLES20.glGetProgramiv(program, pname, params);
    }

    @BridgeInterface
    public void glGetRenderbufferParameteriv(int target, int pname, IntBuffer params) {
        GLES20.glGetRenderbufferParameteriv(target, pname, params);
    }

    @BridgeInterface
    public String glGetShaderInfoLog(int shader) {
        return GLES20.glGetShaderInfoLog(shader);
    }

    @BridgeInterface
    public void glGetShaderiv(int shader, int pname, IntBuffer params) {
        GLES20.glGetShaderiv(shader, pname, params);
    }

    @BridgeInterface
    public void glGetShaderPrecisionFormat(int shadertype, int precisiontype, IntBuffer range, IntBuffer precision) {
        GLES20.glGetShaderPrecisionFormat(shadertype, precisiontype, range, precision);
    }

    @BridgeInterface
    public String glGetString(int name) {
        return GLES20.glGetString(name);
    }

    @BridgeInterface
    public void glGetTexParameterfv(int target, int pname, FloatBuffer params) {
        GLES20.glGetTexParameterfv(target, pname, params);
    }

    @BridgeInterface
    public void glGetTexParameteriv(int target, int pname, IntBuffer params) {
        GLES20.glGetTexParameteriv(target, pname, params);
    }

    @BridgeInterface
    public void glGetUniformfv(int program, int location, FloatBuffer params) {
        GLES20.glGetUniformfv(program, location, params);
    }

    @BridgeInterface
    public void glGetUniformiv(int program, int location, IntBuffer params) {
        GLES20.glGetUniformiv(program, location, params);
    }

    @BridgeInterface
    public int glGetUniformLocation(int program, String name) {
        return GLES20.glGetUniformLocation(program, name);
    }

    @BridgeInterface
    public void glGetVertexAttribfv(int index, int pname, FloatBuffer params) {
        GLES20.glGetVertexAttribfv(index, pname, params);
    }

    @BridgeInterface
    public void glGetVertexAttribiv(int index, int pname, IntBuffer params) {
        GLES20.glGetVertexAttribiv(index, pname, params);
    }

    @BridgeInterface
    public void glHint(int target, int mode) {
        GLES20.glHint(target, mode);
    }

    @BridgeInterface
    public boolean glIsBuffer(int buffer) {
        return GLES20.glIsBuffer(buffer);
    }

    @BridgeInterface
    public boolean glIsEnabled(int cap) {
        return GLES20.glIsEnabled(cap);
    }

    @BridgeInterface
    public boolean glIsFramebuffer(int framebuffer) {
        return GLES20.glIsFramebuffer(framebuffer);
    }

    @BridgeInterface
    public boolean glIsProgram(int program) {
        return GLES20.glIsProgram(program);
    }

    @BridgeInterface
    public boolean glIsRenderbuffer(int renderbuffer) {
        return GLES20.glIsRenderbuffer(renderbuffer);
    }

    @BridgeInterface
    public boolean glIsShader(int shader) {
        return GLES20.glIsShader(shader);
    }

    @BridgeInterface
    public boolean glIsTexture(int texture) {
        return GLES20.glIsTexture(texture);
    }

    @BridgeInterface
    public void glLineWidth(float width) {
        GLES20.glLineWidth(width);
    }

    @BridgeInterface
    public void glLinkProgram(int program) {
        GLES20.glLinkProgram(program);
    }

    @BridgeInterface
    public void glPixelStorei(int pname, int param) {
        GLES20.glPixelStorei(pname, param);
    }

    @BridgeInterface
    public void glPolygonOffset(float factor, float units) {
        GLES20.glPolygonOffset(factor, units);
    }

    @BridgeInterface
    public void glReadPixels(int x, int y, int width, int height, int format, int type, Buffer pixels) {
        GLES20.glReadPixels(x, y, width, height, format, type, bufferAddress(pixels));
    }

    @BridgeInterface
    public void glReleaseShaderCompiler() {
        GLES20.glReleaseShaderCompiler();
    }

    @BridgeInterface
    public void glRenderbufferStorage(int target, int internalformat, int width, int height) {
        GLES20.glRenderbufferStorage(target, internalformat, width, height);
    }

    @BridgeInterface
    public void glSampleCoverage(float value, boolean invert) {
        GLES20.glSampleCoverage(value, invert);
    }

    @BridgeInterface
    public void glScissor(int x, int y, int width, int height) {
        GLES20.glScissor(x, y, width, height);
    }

    @BridgeInterface
    public void glShaderSource(int shader, String string) {
        GLES20.glShaderSource(shader, string);
    }

    @BridgeInterface
    public void glStencilFunc(int func, int ref, int mask) {
        GLES20.glStencilFunc(func, ref, mask);
    }

    @BridgeInterface
    public void glStencilFuncSeparate(int face, int func, int ref, int mask) {
        GLES20.glStencilFuncSeparate(face, func, ref, mask);
    }

    @BridgeInterface
    public void glStencilMask(int mask) {
        GLES20.glStencilMask(mask);
    }

    @BridgeInterface
    public void glStencilMaskSeparate(int face, int mask) {
        GLES20.glStencilMaskSeparate(face, mask);
    }

    @BridgeInterface
    public void glStencilOp(int fail, int zfail, int zpass) {
        GLES20.glStencilOp(fail, zfail, zpass);
    }

    @BridgeInterface
    public void glStencilOpSeparate(int face, int fail, int zfail, int zpass) {
        GLES20.glStencilOpSeparate(face, fail, zfail, zpass);
    }

    @BridgeInterface
    public void glTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, Buffer pixels) {
        GLES20.glTexImage2D(target, level, internalformat, width, height, border, format, type, bufferAddress(pixels));
    }

    @BridgeInterface
    public void glTexParameterf(int target, int pname, float param) {
        GLES20.glTexParameterf(target, pname, param);
    }

    @BridgeInterface
    public void glTexParameterfv(int target, int pname, FloatBuffer params) {
        GLES20.glTexParameterfv(target, pname, params);
    }

    @BridgeInterface
    public void glTexParameteri(int target, int pname, int param) {
        GLES20.glTexParameteri(target, pname, param);
    }

    @BridgeInterface
    public void glTexParameteriv(int target, int pname, IntBuffer params) {
        GLES20.glTexParameteriv(target, pname, params);
    }

    @BridgeInterface
    public void glTexSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, Buffer pixels) {
        GLES20.glTexSubImage2D(target, level, xoffset, yoffset, width, height, format, type, bufferAddress(pixels));
    }

    @BridgeInterface
    public void glUniform1f(int location, float x) {
        GLES20.glUniform1f(location, x);
    }

    @BridgeInterface
    public void glUniform1fv(int location, int count, float[] v, int offset) {
        try(MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer values = stack.mallocFloat(count);
            values.put(v, offset, count);
            values.position(0);
            GLES20.nglUniform1fv(location, count, MemoryUtil.memAddress(values));
        }
    }

    @BridgeInterface
    public void glUniform1fv(int location, int count, FloatBuffer v) {
        GLES20.nglUniform1fv(location, count, bufferAddress(v));
    }

    @BridgeInterface
    public void glUniform1i(int location, int x) {
        GLES20.glUniform1i(location, x);
    }

    @BridgeInterface
    public void glUniform1iv(int location, int count, int[] v, int offset) {
        try(MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer values = stack.mallocInt(count);
            values.put(v, offset, count);
            values.position(0);
            GLES20.nglUniform1iv(location, count, MemoryUtil.memAddress(values));
        }
    }

    @BridgeInterface
    public void glUniform1iv(int location, int count, IntBuffer v) {
        GLES20.nglUniform1iv(location, count, bufferAddress(v));
    }

    @BridgeInterface
    public void glUniform2f(int location, float x, float y) {
        GLES20.glUniform2f(location, x, y);
    }

    @BridgeInterface
    public void glUniform2fv(int location, int count, float[] v, int offset) {
        try(MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer values = stack.mallocFloat(count * 2);
            values.put(v, offset, count * 2);
            values.position(0);
            GLES20.nglUniform2fv(location, count, MemoryUtil.memAddress(values));
        }
    }

    @BridgeInterface
    public void glUniform2fv(int location, int count, FloatBuffer v) {
        GLES20.nglUniform2fv(location, count, bufferAddress(v));
    }

    @BridgeInterface
    public void glUniform2i(int location, int x, int y) {
        GLES20.glUniform2i(location, x, y);
    }

    @BridgeInterface
    public void glUniform2iv(int location, int count, int[] v, int offset) {
        try(MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer values = stack.mallocInt(count * 2);
            values.put(v, offset, count * 2);
            values.position(0);
            GLES20.nglUniform2iv(location, count, MemoryUtil.memAddress(values));
        }
    }

    @BridgeInterface
    public void glUniform2iv(int location, int count, IntBuffer v) {
        GLES20.nglUniform2iv(location, count, bufferAddress(v));
    }

    @BridgeInterface
    public void glUniform3f(int location, float x, float y, float z) {
        GLES20.glUniform3f(location, x, y, z);
    }

    @BridgeInterface
    public void glUniform3fv(int location, int count, float[] v, int offset) {
        try(MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer values = stack.mallocFloat(count * 3);
            values.put(v, offset, count * 3);
            values.position(0);
            GLES20.nglUniform3fv(location, count, MemoryUtil.memAddress(values));
        }
    }

    @BridgeInterface
    public void glUniform3fv(int location, int count, FloatBuffer v) {
        GLES20.nglUniform3fv(location, count, bufferAddress(v));
    }

    @BridgeInterface
    public void glUniform3i(int location, int x, int y, int z) {
        GLES20.glUniform3i(location, x, y, z);
    }

    @BridgeInterface
    public void glUniform3iv(int location, int count, int[] v, int offset) {
        try(MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer values = stack.mallocInt(count * 3);
            values.put(v, offset, count * 3);
            values.position(0);
            GLES20.nglUniform3iv(location, count, MemoryUtil.memAddress(values));
        }
    }

    @BridgeInterface
    public void glUniform3iv(int location, int count, IntBuffer v) {
        GLES20.nglUniform3iv(location, count, bufferAddress(v));
    }

    @BridgeInterface
    public void glUniform4f(int location, float x, float y, float z, float w) {
        GLES20.glUniform4f(location, x, y, z, w);
    }

    @BridgeInterface
    public void glUniform4fv(int location, int count, float[] v, int offset) {
        try(MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer values = stack.mallocFloat(count * 4);
            values.put(v, offset, count * 4);
            values.position(0);
            GLES20.nglUniform4fv(location, count, MemoryUtil.memAddress(values));
        }
    }

    @BridgeInterface
    public void glUniform4fv(int location, int count, FloatBuffer v) {
        GLES20.nglUniform4fv(location, count, bufferAddress(v));
    }

    @BridgeInterface
    public void glUniform4i(int location, int x, int y, int z, int w) {
        GLES20.glUniform4i(location, x, y, z, w);
    }

    @BridgeInterface
    public void glUniform4iv(int location, int count, int[] v, int offset) {
        try(MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer values = stack.mallocInt(count * 4);
            values.put(v, offset, count * 4);
            values.position(0);
            GLES20.nglUniform4iv(location, count, MemoryUtil.memAddress(values));
        }
    }

    @BridgeInterface
    public void glUniform4iv(int location, int count, IntBuffer v) {
        GLES20.nglUniform4iv(location, count, bufferAddress(v));
    }

    @BridgeInterface
    public void glUniformMatrix2fv(int location, int count, boolean transpose, float[] value, int offset) {
        try(MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer values = stack.mallocFloat(count * 4);
            values.put(value, offset, count * 4);
            values.position(0);
            GLES20.nglUniformMatrix2fv(location, count, transpose, MemoryUtil.memAddress(values));
        }
    }

    @BridgeInterface
    public void glUniformMatrix2fv(int location, int count, boolean transpose, FloatBuffer value) {
        GLES20.nglUniformMatrix2fv(location, count, transpose, bufferAddress(value));
    }

    @BridgeInterface
    public void glUniformMatrix3fv(int location, int count, boolean transpose, float[] value, int offset) {
        try(MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer values = stack.mallocFloat(count * 9);
            values.put(value, offset, count * 9);
            values.position(0);
            GLES20.nglUniformMatrix3fv(location, count, transpose, MemoryUtil.memAddress(values));
        }
    }

    @BridgeInterface
    public void glUniformMatrix3fv(int location, int count, boolean transpose, FloatBuffer value) {
        GLES20.nglUniformMatrix3fv(location, count, transpose, bufferAddress(value));
    }

    @BridgeInterface
    public void glUniformMatrix4fv(int location, int count, boolean transpose, float[] value, int offset) {
        try(MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer values = stack.mallocFloat(count * 16);
            values.put(value, offset, count * 16);
            values.position(0);
            GLES20.nglUniformMatrix4fv(location, count, transpose, MemoryUtil.memAddress(values));
        }
    }

    @BridgeInterface
    public void glUniformMatrix4fv(int location, int count, boolean transpose, FloatBuffer value) {
        GLES20.nglUniformMatrix4fv(location, count, transpose, bufferAddress(value));
    }

    @BridgeInterface
    public void glUseProgram(int program) {
        GLES20.glUseProgram(program);
    }

    @BridgeInterface
    public void glValidateProgram(int program) {
        GLES20.glValidateProgram(program);
    }

    @BridgeInterface
    public void glVertexAttrib1f(int indx, float x) {
        GLES20.glVertexAttrib1f(indx, x);
    }

    @BridgeInterface
    public void glVertexAttrib1fv(int indx, FloatBuffer values) {
        GLES20.glVertexAttrib1fv(indx, values);
    }

    @BridgeInterface
    public void glVertexAttrib2f(int indx, float x, float y) {
        GLES20.glVertexAttrib2f(indx, x, y);
    }

    @BridgeInterface
    public void glVertexAttrib2fv(int indx, FloatBuffer values) {
        GLES20.glVertexAttrib2fv(indx, values);
    }

    @BridgeInterface
    public void glVertexAttrib3f(int indx, float x, float y, float z) {
        GLES20.glVertexAttrib3f(indx, x, y, z);
    }

    @BridgeInterface
    public void glVertexAttrib3fv(int indx, FloatBuffer values) {
        GLES20.glVertexAttrib3fv(indx, values);
    }

    @BridgeInterface
    public void glVertexAttrib4f(int indx, float x, float y, float z, float w) {
        GLES20.glVertexAttrib4f(indx, x, y, z, w);
    }

    @BridgeInterface
    public void glVertexAttrib4fv(int indx, FloatBuffer values) {
        GLES20.glVertexAttrib4fv(indx, values);
    }

    @BridgeInterface
    public void glVertexAttribPointer(int indx, int size, int type, boolean normalized, int stride, int ptr) {
        GLES20.glVertexAttribPointer(indx, size, type, normalized, stride, ptr);
    }

    @BridgeInterface
    public void glViewport(int x, int y, int width, int height) {
        GLES20.glViewport(x, y, width, height);
    }

    // The OpenGL ES 3.0 half of the interface.

    @BridgeInterface
    public void glBeginQuery(int target, int id) {
        GLES30.glBeginQuery(target, id);
    }

    @BridgeInterface
    public void glBeginTransformFeedback(int primitiveMode) {
        GLES30.glBeginTransformFeedback(primitiveMode);
    }

    @BridgeInterface
    public void glBindBufferBase(int target, int index, int buffer) {
        GLES30.glBindBufferBase(target, index, buffer);
    }

    @BridgeInterface
    public void glBindBufferRange(int target, int index, int buffer, int offset, int size) {
        GLES30.glBindBufferRange(target, index, buffer, offset, size);
    }

    @BridgeInterface
    public void glBindSampler(int unit, int sampler) {
        GLES30.glBindSampler(unit, sampler);
    }

    @BridgeInterface
    public void glBindTransformFeedback(int target, int id) {
        GLES30.glBindTransformFeedback(target, id);
    }

    @BridgeInterface
    public void glBindVertexArray(int array) {
        GLES30.glBindVertexArray(array);
    }

    @BridgeInterface
    public void glBlitFramebuffer(int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
        GLES30.glBlitFramebuffer(srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
    }

    @BridgeInterface
    public void glClearBufferfi(int buffer, int drawbuffer, float depth, int stencil) {
        GLES30.glClearBufferfi(buffer, drawbuffer, depth, stencil);
    }

    @BridgeInterface
    public void glClearBufferfv(int buffer, int drawbuffer, FloatBuffer value) {
        GLES30.glClearBufferfv(buffer, drawbuffer, value);
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
    public void glCopyBufferSubData(int readTarget, int writeTarget, int readOffset, int writeOffset, int size) {
        GLES30.glCopyBufferSubData(readTarget, writeTarget, readOffset, writeOffset, size);
    }

    @BridgeInterface
    public void glCopyTexSubImage3D(int target, int level, int xoffset, int yoffset, int zoffset, int x, int y, int width, int height) {
        GLES30.glCopyTexSubImage3D(target, level, xoffset, yoffset, zoffset, x, y, width, height);
    }

    @BridgeInterface
    public void glDeleteQueries(int n, IntBuffer ids) {
        GLES30.nglDeleteQueries(n, bufferAddress(ids));
    }

    @BridgeInterface
    public void glDeleteSamplers(int count, IntBuffer samplers) {
        GLES30.nglDeleteSamplers(count, bufferAddress(samplers));
    }

    @BridgeInterface
    public void glDeleteTransformFeedbacks(int n, IntBuffer ids) {
        GLES30.nglDeleteTransformFeedbacks(n, bufferAddress(ids));
    }

    @BridgeInterface
    public void glDeleteVertexArrays(int n, IntBuffer arrays) {
        GLES30.nglDeleteVertexArrays(n, bufferAddress(arrays));
    }

    @BridgeInterface
    public void glDrawArraysInstanced(int mode, int first, int count, int instanceCount) {
        GLES30.glDrawArraysInstanced(mode, first, count, instanceCount);
    }

    @BridgeInterface
    public void glDrawBuffers(int n, IntBuffer bufs) {
        GLES30.nglDrawBuffers(n, bufferAddress(bufs));
    }

    @BridgeInterface
    public void glDrawElementsInstanced(int mode, int count, int type, int indicesOffset, int instanceCount) {
        GLES30.glDrawElementsInstanced(mode, count, type, indicesOffset, instanceCount);
    }

    @BridgeInterface
    public void glDrawRangeElements(int mode, int start, int end, int count, int type, Buffer indices) {
        GLES30.glDrawRangeElements(mode, start, end, count, type, bufferAddress(indices));
    }

    @BridgeInterface
    public void glDrawRangeElements(int mode, int start, int end, int count, int type, int offset) {
        GLES30.glDrawRangeElements(mode, start, end, count, type, offset);
    }

    @BridgeInterface
    public void glEndQuery(int target) {
        GLES30.glEndQuery(target);
    }

    @BridgeInterface
    public void glEndTransformFeedback() {
        GLES30.glEndTransformFeedback();
    }

    @BridgeInterface
    public void glFlushMappedBufferRange(int target, int offset, int length) {
        GLES30.glFlushMappedBufferRange(target, offset, length);
    }

    @BridgeInterface
    public void glFramebufferTextureLayer(int target, int attachment, int texture, int level, int layer) {
        GLES30.glFramebufferTextureLayer(target, attachment, texture, level, layer);
    }

    @BridgeInterface
    public void glGenQueries(int n, IntBuffer ids) {
        GLES30.nglGenQueries(n, bufferAddress(ids));
    }

    @BridgeInterface
    public void glGenSamplers(int count, IntBuffer samplers) {
        GLES30.nglGenSamplers(count, bufferAddress(samplers));
    }

    @BridgeInterface
    public void glGenTransformFeedbacks(int n, IntBuffer ids) {
        GLES30.nglGenTransformFeedbacks(n, bufferAddress(ids));
    }

    @BridgeInterface
    public void glGenVertexArrays(int n, IntBuffer arrays) {
        GLES30.nglGenVertexArrays(n, bufferAddress(arrays));
    }

    @BridgeInterface
    public void glGetActiveUniformBlockiv(int program, int uniformBlockIndex, int pname, IntBuffer params) {
        GLES30.glGetActiveUniformBlockiv(program, uniformBlockIndex, pname, params);
    }

    @BridgeInterface
    public void glGetActiveUniformBlockName(int program, int uniformBlockIndex, Buffer length, Buffer uniformBlockName) {
        if (!(uniformBlockName instanceof ByteBuffer)) {
            throw new IllegalArgumentException("Unsupported buffer type for a uniform block name: " + uniformBlockName.getClass().getName());
        }
        if (length != null && !(length instanceof IntBuffer)) {
            throw new IllegalArgumentException("Unsupported buffer type for a uniform block name length: " + length.getClass().getName());
        }
        GLES30.glGetActiveUniformBlockName(program, uniformBlockIndex, (IntBuffer)length, (ByteBuffer)uniformBlockName);
    }

    @BridgeInterface
    public void glGetActiveUniformsiv(int program, int uniformCount, IntBuffer uniformIndices, int pname, IntBuffer params) {
        GLES30.nglGetActiveUniformsiv(program, uniformCount, bufferAddress(uniformIndices), pname, bufferAddress(params));
    }

    @BridgeInterface
    public void glGetBufferParameteri64v(int target, int pname, LongBuffer params) {
        GLES30.glGetBufferParameteri64v(target, pname, params);
    }

    @BridgeInterface
    public Buffer glGetBufferPointerv(int target, int pname) {
        long pointer = GLES30.glGetBufferPointer(target, pname);
        if (pointer == 0L) {
            return null;
        }
        try(MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer mapped = stack.mallocLong(1);
            glGetBufferParameteri64v(target, Gl.bufferMapLength, mapped);
            return MemoryUtil.memByteBuffer(pointer, (int)Math.min(mapped.get(0), Integer.MAX_VALUE));
        }
    }

    @BridgeInterface
    public int glGetFragDataLocation(int program, String name) {
        return GLES30.glGetFragDataLocation(program, name);
    }

    @BridgeInterface
    public void glGetInteger64v(int pname, LongBuffer params) {
        GLES30.glGetInteger64v(pname, params);
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
    public void glGetSamplerParameterfv(int sampler, int pname, FloatBuffer params) {
        GLES30.glGetSamplerParameterfv(sampler, pname, params);
    }

    @BridgeInterface
    public void glGetSamplerParameteriv(int sampler, int pname, IntBuffer params) {
        GLES30.glGetSamplerParameteriv(sampler, pname, params);
    }

    @BridgeInterface
    public String glGetStringi(int name, int index) {
        return GLES30.glGetStringi(name, index);
    }

    @BridgeInterface
    public int glGetUniformBlockIndex(int program, String uniformBlockName) {
        return GLES30.glGetUniformBlockIndex(program, uniformBlockName);
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
    public void glGetUniformuiv(int program, int location, IntBuffer params) {
        GLES30.glGetUniformuiv(program, location, params);
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
    public void glInvalidateFramebuffer(int target, int numAttachments, IntBuffer attachments) {
        GLES30.nglInvalidateFramebuffer(target, numAttachments, bufferAddress(attachments));
    }

    @BridgeInterface
    public void glInvalidateSubFramebuffer(int target, int numAttachments, IntBuffer attachments, int x, int y, int width, int height) {
        GLES30.nglInvalidateSubFramebuffer(target, numAttachments, bufferAddress(attachments), x, y, width, height);
    }

    @BridgeInterface
    public boolean glIsQuery(int id) {
        return GLES30.glIsQuery(id);
    }

    @BridgeInterface
    public boolean glIsSampler(int sampler) {
        return GLES30.glIsSampler(sampler);
    }

    @BridgeInterface
    public boolean glIsTransformFeedback(int id) {
        return GLES30.glIsTransformFeedback(id);
    }

    @BridgeInterface
    public boolean glIsVertexArray(int array) {
        return GLES30.glIsVertexArray(array);
    }

    @BridgeInterface
    public void glPauseTransformFeedback() {
        GLES30.glPauseTransformFeedback();
    }

    @BridgeInterface
    public void glProgramParameteri(int program, int pname, int value) {
        GLES30.glProgramParameteri(program, pname, value);
    }

    @BridgeInterface
    public void glReadBuffer(int mode) {
        GLES30.glReadBuffer(mode);
    }

    @BridgeInterface
    public void glRenderbufferStorageMultisample(int target, int samples, int internalformat, int width, int height) {
        GLES30.glRenderbufferStorageMultisample(target, samples, internalformat, width, height);
    }

    @BridgeInterface
    public void glResumeTransformFeedback() {
        GLES30.glResumeTransformFeedback();
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
    public void glSamplerParameteri(int sampler, int pname, int param) {
        GLES30.glSamplerParameteri(sampler, pname, param);
    }

    @BridgeInterface
    public void glSamplerParameteriv(int sampler, int pname, IntBuffer param) {
        GLES30.glSamplerParameteriv(sampler, pname, param);
    }

    @BridgeInterface
    public void glTexImage3D(int target, int level, int internalformat, int width, int height, int depth, int border, int format, int type, Buffer pixels) {
        GLES30.glTexImage3D(target, level, internalformat, width, height, depth, border, format, type, bufferAddress(pixels));
    }

    @BridgeInterface
    public void glTexImage3D(int target, int level, int internalformat, int width, int height, int depth, int border, int format, int type, int offset) {
        GLES30.glTexImage3D(target, level, internalformat, width, height, depth, border, format, type, offset);
    }

    @BridgeInterface
    public void glTexSubImage3D(int target, int level, int xoffset, int yoffset, int zoffset, int width, int height, int depth, int format, int type, Buffer pixels) {
        GLES30.glTexSubImage3D(target, level, xoffset, yoffset, zoffset, width, height, depth, format, type, bufferAddress(pixels));
    }

    @BridgeInterface
    public void glTexSubImage3D(int target, int level, int xoffset, int yoffset, int zoffset, int width, int height, int depth, int format, int type, int offset) {
        GLES30.glTexSubImage3D(target, level, xoffset, yoffset, zoffset, width, height, depth, format, type, offset);
    }

    @BridgeInterface
    public void glTransformFeedbackVaryings(int program, String[] varyings, int bufferMode) {
        GLES30.glTransformFeedbackVaryings(program, varyings, bufferMode);
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
    public void glUniformBlockBinding(int program, int uniformBlockIndex, int uniformBlockBinding) {
        GLES30.glUniformBlockBinding(program, uniformBlockIndex, uniformBlockBinding);
    }

    @BridgeInterface
    public void glUniformMatrix2x3fv(int location, int count, boolean transpose, FloatBuffer value) {
        GLES30.nglUniformMatrix2x3fv(location, count, transpose, bufferAddress(value));
    }

    @BridgeInterface
    public void glUniformMatrix2x4fv(int location, int count, boolean transpose, FloatBuffer value) {
        GLES30.nglUniformMatrix2x4fv(location, count, transpose, bufferAddress(value));
    }

    @BridgeInterface
    public void glUniformMatrix3x2fv(int location, int count, boolean transpose, FloatBuffer value) {
        GLES30.nglUniformMatrix3x2fv(location, count, transpose, bufferAddress(value));
    }

    @BridgeInterface
    public void glUniformMatrix3x4fv(int location, int count, boolean transpose, FloatBuffer value) {
        GLES30.nglUniformMatrix3x4fv(location, count, transpose, bufferAddress(value));
    }

    @BridgeInterface
    public void glUniformMatrix4x2fv(int location, int count, boolean transpose, FloatBuffer value) {
        GLES30.nglUniformMatrix4x2fv(location, count, transpose, bufferAddress(value));
    }

    @BridgeInterface
    public void glUniformMatrix4x3fv(int location, int count, boolean transpose, FloatBuffer value) {
        GLES30.nglUniformMatrix4x3fv(location, count, transpose, bufferAddress(value));
    }

    @BridgeInterface
    public boolean glUnmapBuffer(int target) {
        return GLES30.glUnmapBuffer(target);
    }

    @BridgeInterface
    public void glVertexAttribDivisor(int index, int divisor) {
        GLES30.glVertexAttribDivisor(index, divisor);
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
    public void glVertexAttribIPointer(int index, int size, int type, int stride, int offset) {
        GLES30.glVertexAttribIPointer(index, size, type, stride, offset);
    }
}

