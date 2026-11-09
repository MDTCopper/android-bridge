package copper.bridge.v9;

import copper.bridge.annotation.BridgeOverride;

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

    @BridgeOverride
    public void glActiveTexture(int texture) {
        GLES20.glActiveTexture(texture);
    }

    @BridgeOverride
    public void glAttachShader(int program, int shader) {
        GLES20.glAttachShader(program, shader);
    }

    @BridgeOverride
    public void glBindAttribLocation(int program, int index, String name) {
        GLES20.glBindAttribLocation(program, index, name);
    }

    @BridgeOverride
    public void glBindBuffer(int target, int buffer) {
        GLES20.glBindBuffer(target, buffer);
    }

    @BridgeOverride
    public void glBindFramebuffer(int target, int framebuffer) {
        GLES20.glBindFramebuffer(target, framebuffer);
    }

    @BridgeOverride
    public void glBindRenderbuffer(int target, int renderbuffer) {
        GLES20.glBindRenderbuffer(target, renderbuffer);
    }

    @BridgeOverride
    public void glBindTexture(int target, int texture) {
        GLES20.glBindTexture(target, texture);
    }

    @BridgeOverride
    public void glBlendColor(float red, float green, float blue, float alpha) {
        GLES20.glBlendColor(red, green, blue, alpha);
    }

    @BridgeOverride
    public void glBlendEquation(int mode) {
        GLES20.glBlendEquation(mode);
    }

    @BridgeOverride
    public void glBlendEquationSeparate(int modeRGB, int modeAlpha) {
        GLES20.glBlendEquationSeparate(modeRGB, modeAlpha);
    }

    @BridgeOverride
    public void glBlendFunc(int sfactor, int dfactor) {
        GLES20.glBlendFunc(sfactor, dfactor);
    }

    @BridgeOverride
    public void glBlendFuncSeparate(int srcRGB, int dstRGB, int srcAlpha, int dstAlpha) {
        GLES20.glBlendFuncSeparate(srcRGB, dstRGB, srcAlpha, dstAlpha);
    }

    @BridgeOverride
    public void glBufferData(int target, int size, Buffer data, int usage) {
        if (data == null) {
            GLES20.glBufferData(target, size, usage);
        } else {
            GLES20.nglBufferData(target, size, bufferAddress(data), usage);
        }
    }

    @BridgeOverride
    public void glBufferSubData(int target, int offset, int size, Buffer data) {
        GLES20.nglBufferSubData(target, offset, size, bufferAddress(data));
    }

    @BridgeOverride
    public int glCheckFramebufferStatus(int target) {
        return GLES20.glCheckFramebufferStatus(target);
    }

    @BridgeOverride
    public void glClear(int mask) {
        GLES20.glClear(mask);
    }

    @BridgeOverride
    public void glClearColor(float red, float green, float blue, float alpha) {
        GLES20.glClearColor(red, green, blue, alpha);
    }

    @BridgeOverride
    public void glClearDepthf(float depth) {
        GLES20.glClearDepthf(depth);
    }

    @BridgeOverride
    public void glClearStencil(int s) {
        GLES20.glClearStencil(s);
    }

    @BridgeOverride
    public void glColorMask(boolean red, boolean green, boolean blue, boolean alpha) {
        GLES20.glColorMask(red, green, blue, alpha);
    }

    @BridgeOverride
    public void glCompileShader(int shader) {
        GLES20.glCompileShader(shader);
    }

    @BridgeOverride
    public void glCompressedTexImage2D(int target, int level, int internalformat, int width, int height, int border, int imageSize, Buffer data) {
        GLES20.glCompressedTexImage2D(target, level, internalformat, width, height, border, imageSize, bufferAddress(data));
    }

    @BridgeOverride
    public void glCompressedTexSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format, int imageSize, Buffer data) {
        GLES20.glCompressedTexSubImage2D(target, level, xoffset, yoffset, width, height, format, imageSize, bufferAddress(data));
    }

    @BridgeOverride
    public void glCopyTexImage2D(int target, int level, int internalformat, int x, int y, int width, int height, int border) {
        GLES20.glCopyTexImage2D(target, level, internalformat, x, y, width, height, border);
    }

    @BridgeOverride
    public void glCopyTexSubImage2D(int target, int level, int xoffset, int yoffset, int x, int y, int width, int height) {
        GLES20.glCopyTexSubImage2D(target, level, xoffset, yoffset, x, y, width, height);
    }

    @BridgeOverride
    public int glCreateProgram() {
        return GLES20.glCreateProgram();
    }

    @BridgeOverride
    public int glCreateShader(int type) {
        return GLES20.glCreateShader(type);
    }

    @BridgeOverride
    public void glCullFace(int mode) {
        GLES20.glCullFace(mode);
    }

    @BridgeOverride
    public void glDeleteBuffer(int buffer) {
        GLES20.glDeleteBuffers(buffer);
    }

    @BridgeOverride
    public void glDeleteFramebuffer(int framebuffer) {
        GLES20.glDeleteFramebuffers(framebuffer);
    }

    @BridgeOverride
    public void glDeleteProgram(int program) {
        GLES20.glDeleteProgram(program);
    }

    @BridgeOverride
    public void glDeleteRenderbuffer(int renderbuffer) {
        GLES20.glDeleteRenderbuffers(renderbuffer);
    }

    @BridgeOverride
    public void glDeleteShader(int shader) {
        GLES20.glDeleteShader(shader);
    }

    @BridgeOverride
    public void glDeleteTexture(int texture) {
        GLES20.glDeleteTextures(texture);
    }

    @BridgeOverride
    public void glDepthFunc(int func) {
        GLES20.glDepthFunc(func);
    }

    @BridgeOverride
    public void glDepthMask(boolean flag) {
        GLES20.glDepthMask(flag);
    }

    @BridgeOverride
    public void glDepthRangef(float zNear, float zFar) {
        GLES20.glDepthRangef(zNear, zFar);
    }

    @BridgeOverride
    public void glDetachShader(int program, int shader) {
        GLES20.glDetachShader(program, shader);
    }

    @BridgeOverride
    public void glDisable(int cap) {
        GLES20.glDisable(cap);
    }

    @BridgeOverride
    public void glDisableVertexAttribArray(int index) {
        GLES20.glDisableVertexAttribArray(index);
    }

    @BridgeOverride
    public void glDrawArrays(int mode, int first, int count) {
        GLES20.glDrawArrays(mode, first, count);
    }

    @BridgeOverride
    public void glDrawElements(int mode, int count, int type, Buffer indices) {
        GLES20.glDrawElements(mode, count, type, bufferAddress(indices));
    }

    @BridgeOverride
    public void glDrawElements(int mode, int count, int type, int indices) {
        GLES20.glDrawElements(mode, count, type, indices);
    }

    @BridgeOverride
    public void glEnable(int cap) {
        GLES20.glEnable(cap);
    }

    @BridgeOverride
    public void glEnableVertexAttribArray(int index) {
        GLES20.glEnableVertexAttribArray(index);
    }

    @BridgeOverride
    public void glFinish() {
        GLES20.glFinish();
    }

    @BridgeOverride
    public void glFlush() {
        GLES20.glFlush();
    }

    @BridgeOverride
    public void glFramebufferRenderbuffer(int target, int attachment, int renderbuffertarget, int renderbuffer) {
        GLES20.glFramebufferRenderbuffer(target, attachment, renderbuffertarget, renderbuffer);
    }

    @BridgeOverride
    public void glFramebufferTexture2D(int target, int attachment, int textarget, int texture, int level) {
        GLES20.glFramebufferTexture2D(target, attachment, textarget, texture, level);
    }

    @BridgeOverride
    public void glFrontFace(int mode) {
        GLES20.glFrontFace(mode);
    }

    @BridgeOverride
    public int glGenBuffer() {
        return GLES20.glGenBuffers();
    }

    @BridgeOverride
    public void glGenerateMipmap(int target) {
        GLES20.glGenerateMipmap(target);
    }

    @BridgeOverride
    public int glGenFramebuffer() {
        return GLES20.glGenFramebuffers();
    }

    @BridgeOverride
    public int glGenRenderbuffer() {
        return GLES20.glGenRenderbuffers();
    }

    @BridgeOverride
    public int glGenTexture() {
        return GLES20.glGenTextures();
    }

    @BridgeOverride
    public String glGetActiveAttrib(int program, int index, IntBuffer size, IntBuffer type) {
        return GLES20.glGetActiveAttrib(program, index, size, type);
    }

    @BridgeOverride
    public String glGetActiveUniform(int program, int index, IntBuffer size, IntBuffer type) {
        return GLES20.glGetActiveUniform(program, index, size, type);
    }

    @BridgeOverride
    public int glGetAttribLocation(int program, String name) {
        return GLES20.glGetAttribLocation(program, name);
    }

    @BridgeOverride
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

    @BridgeOverride
    public void glGetBufferParameteriv(int target, int pname, IntBuffer params) {
        GLES20.glGetBufferParameteriv(target, pname, params);
    }

    @BridgeOverride
    public int glGetError() {
        return GLES20.glGetError();
    }

    @BridgeOverride
    public void glGetFloatv(int pname, FloatBuffer params) {
        GLES20.glGetFloatv(pname, params);
    }

    @BridgeOverride
    public void glGetFramebufferAttachmentParameteriv(int target, int attachment, int pname, IntBuffer params) {
        GLES20.glGetFramebufferAttachmentParameteriv(target, attachment, pname, params);
    }

    @BridgeOverride
    public void glGetIntegerv(int pname, IntBuffer params) {
        GLES20.glGetIntegerv(pname, params);
    }

    @BridgeOverride
    public String glGetProgramInfoLog(int program) {
        return GLES20.glGetProgramInfoLog(program);
    }

    @BridgeOverride
    public void glGetProgramiv(int program, int pname, IntBuffer params) {
        GLES20.glGetProgramiv(program, pname, params);
    }

    @BridgeOverride
    public void glGetRenderbufferParameteriv(int target, int pname, IntBuffer params) {
        GLES20.glGetRenderbufferParameteriv(target, pname, params);
    }

    @BridgeOverride
    public String glGetShaderInfoLog(int shader) {
        return GLES20.glGetShaderInfoLog(shader);
    }

    @BridgeOverride
    public void glGetShaderiv(int shader, int pname, IntBuffer params) {
        GLES20.glGetShaderiv(shader, pname, params);
    }

    @BridgeOverride
    public void glGetShaderPrecisionFormat(int shadertype, int precisiontype, IntBuffer range, IntBuffer precision) {
        GLES20.glGetShaderPrecisionFormat(shadertype, precisiontype, range, precision);
    }

    @BridgeOverride
    public String glGetString(int name) {
        return GLES20.glGetString(name);
    }

    @BridgeOverride
    public void glGetTexParameterfv(int target, int pname, FloatBuffer params) {
        GLES20.glGetTexParameterfv(target, pname, params);
    }

    @BridgeOverride
    public void glGetTexParameteriv(int target, int pname, IntBuffer params) {
        GLES20.glGetTexParameteriv(target, pname, params);
    }

    @BridgeOverride
    public void glGetUniformfv(int program, int location, FloatBuffer params) {
        GLES20.glGetUniformfv(program, location, params);
    }

    @BridgeOverride
    public void glGetUniformiv(int program, int location, IntBuffer params) {
        GLES20.glGetUniformiv(program, location, params);
    }

    @BridgeOverride
    public int glGetUniformLocation(int program, String name) {
        return GLES20.glGetUniformLocation(program, name);
    }

    @BridgeOverride
    public void glGetVertexAttribfv(int index, int pname, FloatBuffer params) {
        GLES20.glGetVertexAttribfv(index, pname, params);
    }

    @BridgeOverride
    public void glGetVertexAttribiv(int index, int pname, IntBuffer params) {
        GLES20.glGetVertexAttribiv(index, pname, params);
    }

    @BridgeOverride
    public void glHint(int target, int mode) {
        GLES20.glHint(target, mode);
    }

    @BridgeOverride
    public boolean glIsBuffer(int buffer) {
        return GLES20.glIsBuffer(buffer);
    }

    @BridgeOverride
    public boolean glIsEnabled(int cap) {
        return GLES20.glIsEnabled(cap);
    }

    @BridgeOverride
    public boolean glIsFramebuffer(int framebuffer) {
        return GLES20.glIsFramebuffer(framebuffer);
    }

    @BridgeOverride
    public boolean glIsProgram(int program) {
        return GLES20.glIsProgram(program);
    }

    @BridgeOverride
    public boolean glIsRenderbuffer(int renderbuffer) {
        return GLES20.glIsRenderbuffer(renderbuffer);
    }

    @BridgeOverride
    public boolean glIsShader(int shader) {
        return GLES20.glIsShader(shader);
    }

    @BridgeOverride
    public boolean glIsTexture(int texture) {
        return GLES20.glIsTexture(texture);
    }

    @BridgeOverride
    public void glLineWidth(float width) {
        GLES20.glLineWidth(width);
    }

    @BridgeOverride
    public void glLinkProgram(int program) {
        GLES20.glLinkProgram(program);
    }

    @BridgeOverride
    public void glPixelStorei(int pname, int param) {
        GLES20.glPixelStorei(pname, param);
    }

    @BridgeOverride
    public void glPolygonOffset(float factor, float units) {
        GLES20.glPolygonOffset(factor, units);
    }

    @BridgeOverride
    public void glReadPixels(int x, int y, int width, int height, int format, int type, Buffer pixels) {
        GLES20.glReadPixels(x, y, width, height, format, type, bufferAddress(pixels));
    }

    @BridgeOverride
    public void glReleaseShaderCompiler() {
        GLES20.glReleaseShaderCompiler();
    }

    @BridgeOverride
    public void glRenderbufferStorage(int target, int internalformat, int width, int height) {
        GLES20.glRenderbufferStorage(target, internalformat, width, height);
    }

    @BridgeOverride
    public void glSampleCoverage(float value, boolean invert) {
        GLES20.glSampleCoverage(value, invert);
    }

    @BridgeOverride
    public void glScissor(int x, int y, int width, int height) {
        GLES20.glScissor(x, y, width, height);
    }

    @BridgeOverride
    public void glShaderSource(int shader, String string) {
        GLES20.glShaderSource(shader, string);
    }

    @BridgeOverride
    public void glStencilFunc(int func, int ref, int mask) {
        GLES20.glStencilFunc(func, ref, mask);
    }

    @BridgeOverride
    public void glStencilFuncSeparate(int face, int func, int ref, int mask) {
        GLES20.glStencilFuncSeparate(face, func, ref, mask);
    }

    @BridgeOverride
    public void glStencilMask(int mask) {
        GLES20.glStencilMask(mask);
    }

    @BridgeOverride
    public void glStencilMaskSeparate(int face, int mask) {
        GLES20.glStencilMaskSeparate(face, mask);
    }

    @BridgeOverride
    public void glStencilOp(int fail, int zfail, int zpass) {
        GLES20.glStencilOp(fail, zfail, zpass);
    }

    @BridgeOverride
    public void glStencilOpSeparate(int face, int fail, int zfail, int zpass) {
        GLES20.glStencilOpSeparate(face, fail, zfail, zpass);
    }

    @BridgeOverride
    public void glTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, Buffer pixels) {
        GLES20.glTexImage2D(target, level, internalformat, width, height, border, format, type, bufferAddress(pixels));
    }

    @BridgeOverride
    public void glTexParameterf(int target, int pname, float param) {
        GLES20.glTexParameterf(target, pname, param);
    }

    @BridgeOverride
    public void glTexParameterfv(int target, int pname, FloatBuffer params) {
        GLES20.glTexParameterfv(target, pname, params);
    }

    @BridgeOverride
    public void glTexParameteri(int target, int pname, int param) {
        GLES20.glTexParameteri(target, pname, param);
    }

    @BridgeOverride
    public void glTexParameteriv(int target, int pname, IntBuffer params) {
        GLES20.glTexParameteriv(target, pname, params);
    }

    @BridgeOverride
    public void glTexSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, Buffer pixels) {
        GLES20.glTexSubImage2D(target, level, xoffset, yoffset, width, height, format, type, bufferAddress(pixels));
    }

    @BridgeOverride
    public void glUniform1f(int location, float x) {
        GLES20.glUniform1f(location, x);
    }

    @BridgeOverride
    public void glUniform1fv(int location, int count, float[] v, int offset) {
        try(MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer values = stack.mallocFloat(count);
            values.put(v, offset, count);
            values.position(0);
            GLES20.nglUniform1fv(location, count, MemoryUtil.memAddress(values));
        }
    }

    @BridgeOverride
    public void glUniform1fv(int location, int count, FloatBuffer v) {
        GLES20.nglUniform1fv(location, count, bufferAddress(v));
    }

    @BridgeOverride
    public void glUniform1i(int location, int x) {
        GLES20.glUniform1i(location, x);
    }

    @BridgeOverride
    public void glUniform1iv(int location, int count, int[] v, int offset) {
        try(MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer values = stack.mallocInt(count);
            values.put(v, offset, count);
            values.position(0);
            GLES20.nglUniform1iv(location, count, MemoryUtil.memAddress(values));
        }
    }

    @BridgeOverride
    public void glUniform1iv(int location, int count, IntBuffer v) {
        GLES20.nglUniform1iv(location, count, bufferAddress(v));
    }

    @BridgeOverride
    public void glUniform2f(int location, float x, float y) {
        GLES20.glUniform2f(location, x, y);
    }

    @BridgeOverride
    public void glUniform2fv(int location, int count, float[] v, int offset) {
        try(MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer values = stack.mallocFloat(count * 2);
            values.put(v, offset, count * 2);
            values.position(0);
            GLES20.nglUniform2fv(location, count, MemoryUtil.memAddress(values));
        }
    }

    @BridgeOverride
    public void glUniform2fv(int location, int count, FloatBuffer v) {
        GLES20.nglUniform2fv(location, count, bufferAddress(v));
    }

    @BridgeOverride
    public void glUniform2i(int location, int x, int y) {
        GLES20.glUniform2i(location, x, y);
    }

    @BridgeOverride
    public void glUniform2iv(int location, int count, int[] v, int offset) {
        try(MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer values = stack.mallocInt(count * 2);
            values.put(v, offset, count * 2);
            values.position(0);
            GLES20.nglUniform2iv(location, count, MemoryUtil.memAddress(values));
        }
    }

    @BridgeOverride
    public void glUniform2iv(int location, int count, IntBuffer v) {
        GLES20.nglUniform2iv(location, count, bufferAddress(v));
    }

    @BridgeOverride
    public void glUniform3f(int location, float x, float y, float z) {
        GLES20.glUniform3f(location, x, y, z);
    }

    @BridgeOverride
    public void glUniform3fv(int location, int count, float[] v, int offset) {
        try(MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer values = stack.mallocFloat(count * 3);
            values.put(v, offset, count * 3);
            values.position(0);
            GLES20.nglUniform3fv(location, count, MemoryUtil.memAddress(values));
        }
    }

    @BridgeOverride
    public void glUniform3fv(int location, int count, FloatBuffer v) {
        GLES20.nglUniform3fv(location, count, bufferAddress(v));
    }

    @BridgeOverride
    public void glUniform3i(int location, int x, int y, int z) {
        GLES20.glUniform3i(location, x, y, z);
    }

    @BridgeOverride
    public void glUniform3iv(int location, int count, int[] v, int offset) {
        try(MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer values = stack.mallocInt(count * 3);
            values.put(v, offset, count * 3);
            values.position(0);
            GLES20.nglUniform3iv(location, count, MemoryUtil.memAddress(values));
        }
    }

    @BridgeOverride
    public void glUniform3iv(int location, int count, IntBuffer v) {
        GLES20.nglUniform3iv(location, count, bufferAddress(v));
    }

    @BridgeOverride
    public void glUniform4f(int location, float x, float y, float z, float w) {
        GLES20.glUniform4f(location, x, y, z, w);
    }

    @BridgeOverride
    public void glUniform4fv(int location, int count, float[] v, int offset) {
        try(MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer values = stack.mallocFloat(count * 4);
            values.put(v, offset, count * 4);
            values.position(0);
            GLES20.nglUniform4fv(location, count, MemoryUtil.memAddress(values));
        }
    }

    @BridgeOverride
    public void glUniform4fv(int location, int count, FloatBuffer v) {
        GLES20.nglUniform4fv(location, count, bufferAddress(v));
    }

    @BridgeOverride
    public void glUniform4i(int location, int x, int y, int z, int w) {
        GLES20.glUniform4i(location, x, y, z, w);
    }

    @BridgeOverride
    public void glUniform4iv(int location, int count, int[] v, int offset) {
        try(MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer values = stack.mallocInt(count * 4);
            values.put(v, offset, count * 4);
            values.position(0);
            GLES20.nglUniform4iv(location, count, MemoryUtil.memAddress(values));
        }
    }

    @BridgeOverride
    public void glUniform4iv(int location, int count, IntBuffer v) {
        GLES20.nglUniform4iv(location, count, bufferAddress(v));
    }

    @BridgeOverride
    public void glUniformMatrix2fv(int location, int count, boolean transpose, float[] value, int offset) {
        try(MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer values = stack.mallocFloat(count * 4);
            values.put(value, offset, count * 4);
            values.position(0);
            GLES20.nglUniformMatrix2fv(location, count, transpose, MemoryUtil.memAddress(values));
        }
    }

    @BridgeOverride
    public void glUniformMatrix2fv(int location, int count, boolean transpose, FloatBuffer value) {
        GLES20.nglUniformMatrix2fv(location, count, transpose, bufferAddress(value));
    }

    @BridgeOverride
    public void glUniformMatrix3fv(int location, int count, boolean transpose, float[] value, int offset) {
        try(MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer values = stack.mallocFloat(count * 9);
            values.put(value, offset, count * 9);
            values.position(0);
            GLES20.nglUniformMatrix3fv(location, count, transpose, MemoryUtil.memAddress(values));
        }
    }

    @BridgeOverride
    public void glUniformMatrix3fv(int location, int count, boolean transpose, FloatBuffer value) {
        GLES20.nglUniformMatrix3fv(location, count, transpose, bufferAddress(value));
    }

    @BridgeOverride
    public void glUniformMatrix4fv(int location, int count, boolean transpose, float[] value, int offset) {
        try(MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer values = stack.mallocFloat(count * 16);
            values.put(value, offset, count * 16);
            values.position(0);
            GLES20.nglUniformMatrix4fv(location, count, transpose, MemoryUtil.memAddress(values));
        }
    }

    @BridgeOverride
    public void glUniformMatrix4fv(int location, int count, boolean transpose, FloatBuffer value) {
        GLES20.nglUniformMatrix4fv(location, count, transpose, bufferAddress(value));
    }

    @BridgeOverride
    public void glUseProgram(int program) {
        GLES20.glUseProgram(program);
    }

    @BridgeOverride
    public void glValidateProgram(int program) {
        GLES20.glValidateProgram(program);
    }

    @BridgeOverride
    public void glVertexAttrib1f(int indx, float x) {
        GLES20.glVertexAttrib1f(indx, x);
    }

    @BridgeOverride
    public void glVertexAttrib1fv(int indx, FloatBuffer values) {
        GLES20.glVertexAttrib1fv(indx, values);
    }

    @BridgeOverride
    public void glVertexAttrib2f(int indx, float x, float y) {
        GLES20.glVertexAttrib2f(indx, x, y);
    }

    @BridgeOverride
    public void glVertexAttrib2fv(int indx, FloatBuffer values) {
        GLES20.glVertexAttrib2fv(indx, values);
    }

    @BridgeOverride
    public void glVertexAttrib3f(int indx, float x, float y, float z) {
        GLES20.glVertexAttrib3f(indx, x, y, z);
    }

    @BridgeOverride
    public void glVertexAttrib3fv(int indx, FloatBuffer values) {
        GLES20.glVertexAttrib3fv(indx, values);
    }

    @BridgeOverride
    public void glVertexAttrib4f(int indx, float x, float y, float z, float w) {
        GLES20.glVertexAttrib4f(indx, x, y, z, w);
    }

    @BridgeOverride
    public void glVertexAttrib4fv(int indx, FloatBuffer values) {
        GLES20.glVertexAttrib4fv(indx, values);
    }

    @BridgeOverride
    public void glVertexAttribPointer(int indx, int size, int type, boolean normalized, int stride, int ptr) {
        GLES20.glVertexAttribPointer(indx, size, type, normalized, stride, ptr);
    }

    @BridgeOverride
    public void glViewport(int x, int y, int width, int height) {
        GLES20.glViewport(x, y, width, height);
    }

    // The OpenGL ES 3.0 half of the interface.

    @BridgeOverride
    public void glBeginQuery(int target, int id) {
        GLES30.glBeginQuery(target, id);
    }

    @BridgeOverride
    public void glBeginTransformFeedback(int primitiveMode) {
        GLES30.glBeginTransformFeedback(primitiveMode);
    }

    @BridgeOverride
    public void glBindBufferBase(int target, int index, int buffer) {
        GLES30.glBindBufferBase(target, index, buffer);
    }

    @BridgeOverride
    public void glBindBufferRange(int target, int index, int buffer, int offset, int size) {
        GLES30.glBindBufferRange(target, index, buffer, offset, size);
    }

    @BridgeOverride
    public void glBindSampler(int unit, int sampler) {
        GLES30.glBindSampler(unit, sampler);
    }

    @BridgeOverride
    public void glBindTransformFeedback(int target, int id) {
        GLES30.glBindTransformFeedback(target, id);
    }

    @BridgeOverride
    public void glBindVertexArray(int array) {
        GLES30.glBindVertexArray(array);
    }

    @BridgeOverride
    public void glBlitFramebuffer(int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
        GLES30.glBlitFramebuffer(srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
    }

    @BridgeOverride
    public void glClearBufferfi(int buffer, int drawbuffer, float depth, int stencil) {
        GLES30.glClearBufferfi(buffer, drawbuffer, depth, stencil);
    }

    @BridgeOverride
    public void glClearBufferfv(int buffer, int drawbuffer, FloatBuffer value) {
        GLES30.glClearBufferfv(buffer, drawbuffer, value);
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
    public void glCopyBufferSubData(int readTarget, int writeTarget, int readOffset, int writeOffset, int size) {
        GLES30.glCopyBufferSubData(readTarget, writeTarget, readOffset, writeOffset, size);
    }

    @BridgeOverride
    public void glCopyTexSubImage3D(int target, int level, int xoffset, int yoffset, int zoffset, int x, int y, int width, int height) {
        GLES30.glCopyTexSubImage3D(target, level, xoffset, yoffset, zoffset, x, y, width, height);
    }

    @BridgeOverride
    public void glDeleteQueries(int n, IntBuffer ids) {
        GLES30.nglDeleteQueries(n, bufferAddress(ids));
    }

    @BridgeOverride
    public void glDeleteSamplers(int count, IntBuffer samplers) {
        GLES30.nglDeleteSamplers(count, bufferAddress(samplers));
    }

    @BridgeOverride
    public void glDeleteTransformFeedbacks(int n, IntBuffer ids) {
        GLES30.nglDeleteTransformFeedbacks(n, bufferAddress(ids));
    }

    @BridgeOverride
    public void glDeleteVertexArrays(int n, IntBuffer arrays) {
        GLES30.nglDeleteVertexArrays(n, bufferAddress(arrays));
    }

    @BridgeOverride
    public void glDrawArraysInstanced(int mode, int first, int count, int instanceCount) {
        GLES30.glDrawArraysInstanced(mode, first, count, instanceCount);
    }

    @BridgeOverride
    public void glDrawBuffers(int n, IntBuffer bufs) {
        GLES30.nglDrawBuffers(n, bufferAddress(bufs));
    }

    @BridgeOverride
    public void glDrawElementsInstanced(int mode, int count, int type, int indicesOffset, int instanceCount) {
        GLES30.glDrawElementsInstanced(mode, count, type, indicesOffset, instanceCount);
    }

    @BridgeOverride
    public void glDrawRangeElements(int mode, int start, int end, int count, int type, Buffer indices) {
        GLES30.glDrawRangeElements(mode, start, end, count, type, bufferAddress(indices));
    }

    @BridgeOverride
    public void glDrawRangeElements(int mode, int start, int end, int count, int type, int offset) {
        GLES30.glDrawRangeElements(mode, start, end, count, type, offset);
    }

    @BridgeOverride
    public void glEndQuery(int target) {
        GLES30.glEndQuery(target);
    }

    @BridgeOverride
    public void glEndTransformFeedback() {
        GLES30.glEndTransformFeedback();
    }

    @BridgeOverride
    public void glFlushMappedBufferRange(int target, int offset, int length) {
        GLES30.glFlushMappedBufferRange(target, offset, length);
    }

    @BridgeOverride
    public void glFramebufferTextureLayer(int target, int attachment, int texture, int level, int layer) {
        GLES30.glFramebufferTextureLayer(target, attachment, texture, level, layer);
    }

    @BridgeOverride
    public void glGenQueries(int n, IntBuffer ids) {
        GLES30.nglGenQueries(n, bufferAddress(ids));
    }

    @BridgeOverride
    public void glGenSamplers(int count, IntBuffer samplers) {
        GLES30.nglGenSamplers(count, bufferAddress(samplers));
    }

    @BridgeOverride
    public void glGenTransformFeedbacks(int n, IntBuffer ids) {
        GLES30.nglGenTransformFeedbacks(n, bufferAddress(ids));
    }

    @BridgeOverride
    public void glGenVertexArrays(int n, IntBuffer arrays) {
        GLES30.nglGenVertexArrays(n, bufferAddress(arrays));
    }

    @BridgeOverride
    public void glGetActiveUniformBlockiv(int program, int uniformBlockIndex, int pname, IntBuffer params) {
        GLES30.glGetActiveUniformBlockiv(program, uniformBlockIndex, pname, params);
    }

    @BridgeOverride
    public void glGetActiveUniformBlockName(int program, int uniformBlockIndex, Buffer length, Buffer uniformBlockName) {
        if (!(uniformBlockName instanceof ByteBuffer)) {
            throw new IllegalArgumentException("Unsupported buffer type for a uniform block name: " + uniformBlockName.getClass().getName());
        }
        if (length != null && !(length instanceof IntBuffer)) {
            throw new IllegalArgumentException("Unsupported buffer type for a uniform block name length: " + length.getClass().getName());
        }
        GLES30.glGetActiveUniformBlockName(program, uniformBlockIndex, (IntBuffer)length, (ByteBuffer)uniformBlockName);
    }

    @BridgeOverride
    public void glGetActiveUniformsiv(int program, int uniformCount, IntBuffer uniformIndices, int pname, IntBuffer params) {
        GLES30.nglGetActiveUniformsiv(program, uniformCount, bufferAddress(uniformIndices), pname, bufferAddress(params));
    }

    @BridgeOverride
    public void glGetBufferParameteri64v(int target, int pname, LongBuffer params) {
        GLES30.glGetBufferParameteri64v(target, pname, params);
    }

    @BridgeOverride
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

    @BridgeOverride
    public int glGetFragDataLocation(int program, String name) {
        return GLES30.glGetFragDataLocation(program, name);
    }

    @BridgeOverride
    public void glGetInteger64v(int pname, LongBuffer params) {
        GLES30.glGetInteger64v(pname, params);
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
    public void glGetSamplerParameterfv(int sampler, int pname, FloatBuffer params) {
        GLES30.glGetSamplerParameterfv(sampler, pname, params);
    }

    @BridgeOverride
    public void glGetSamplerParameteriv(int sampler, int pname, IntBuffer params) {
        GLES30.glGetSamplerParameteriv(sampler, pname, params);
    }

    @BridgeOverride
    public String glGetStringi(int name, int index) {
        return GLES30.glGetStringi(name, index);
    }

    @BridgeOverride
    public int glGetUniformBlockIndex(int program, String uniformBlockName) {
        return GLES30.glGetUniformBlockIndex(program, uniformBlockName);
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
    public void glGetUniformuiv(int program, int location, IntBuffer params) {
        GLES30.glGetUniformuiv(program, location, params);
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
    public void glInvalidateFramebuffer(int target, int numAttachments, IntBuffer attachments) {
        GLES30.nglInvalidateFramebuffer(target, numAttachments, bufferAddress(attachments));
    }

    @BridgeOverride
    public void glInvalidateSubFramebuffer(int target, int numAttachments, IntBuffer attachments, int x, int y, int width, int height) {
        GLES30.nglInvalidateSubFramebuffer(target, numAttachments, bufferAddress(attachments), x, y, width, height);
    }

    @BridgeOverride
    public boolean glIsQuery(int id) {
        return GLES30.glIsQuery(id);
    }

    @BridgeOverride
    public boolean glIsSampler(int sampler) {
        return GLES30.glIsSampler(sampler);
    }

    @BridgeOverride
    public boolean glIsTransformFeedback(int id) {
        return GLES30.glIsTransformFeedback(id);
    }

    @BridgeOverride
    public boolean glIsVertexArray(int array) {
        return GLES30.glIsVertexArray(array);
    }

    @BridgeOverride
    public void glPauseTransformFeedback() {
        GLES30.glPauseTransformFeedback();
    }

    @BridgeOverride
    public void glProgramParameteri(int program, int pname, int value) {
        GLES30.glProgramParameteri(program, pname, value);
    }

    @BridgeOverride
    public void glReadBuffer(int mode) {
        GLES30.glReadBuffer(mode);
    }

    @BridgeOverride
    public void glRenderbufferStorageMultisample(int target, int samples, int internalformat, int width, int height) {
        GLES30.glRenderbufferStorageMultisample(target, samples, internalformat, width, height);
    }

    @BridgeOverride
    public void glResumeTransformFeedback() {
        GLES30.glResumeTransformFeedback();
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
    public void glSamplerParameteri(int sampler, int pname, int param) {
        GLES30.glSamplerParameteri(sampler, pname, param);
    }

    @BridgeOverride
    public void glSamplerParameteriv(int sampler, int pname, IntBuffer param) {
        GLES30.glSamplerParameteriv(sampler, pname, param);
    }

    @BridgeOverride
    public void glTexImage3D(int target, int level, int internalformat, int width, int height, int depth, int border, int format, int type, Buffer pixels) {
        GLES30.glTexImage3D(target, level, internalformat, width, height, depth, border, format, type, bufferAddress(pixels));
    }

    @BridgeOverride
    public void glTexImage3D(int target, int level, int internalformat, int width, int height, int depth, int border, int format, int type, int offset) {
        GLES30.glTexImage3D(target, level, internalformat, width, height, depth, border, format, type, offset);
    }

    @BridgeOverride
    public void glTexSubImage3D(int target, int level, int xoffset, int yoffset, int zoffset, int width, int height, int depth, int format, int type, Buffer pixels) {
        GLES30.glTexSubImage3D(target, level, xoffset, yoffset, zoffset, width, height, depth, format, type, bufferAddress(pixels));
    }

    @BridgeOverride
    public void glTexSubImage3D(int target, int level, int xoffset, int yoffset, int zoffset, int width, int height, int depth, int format, int type, int offset) {
        GLES30.glTexSubImage3D(target, level, xoffset, yoffset, zoffset, width, height, depth, format, type, offset);
    }

    @BridgeOverride
    public void glTransformFeedbackVaryings(int program, String[] varyings, int bufferMode) {
        GLES30.glTransformFeedbackVaryings(program, varyings, bufferMode);
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
    public void glUniformBlockBinding(int program, int uniformBlockIndex, int uniformBlockBinding) {
        GLES30.glUniformBlockBinding(program, uniformBlockIndex, uniformBlockBinding);
    }

    @BridgeOverride
    public void glUniformMatrix2x3fv(int location, int count, boolean transpose, FloatBuffer value) {
        GLES30.nglUniformMatrix2x3fv(location, count, transpose, bufferAddress(value));
    }

    @BridgeOverride
    public void glUniformMatrix2x4fv(int location, int count, boolean transpose, FloatBuffer value) {
        GLES30.nglUniformMatrix2x4fv(location, count, transpose, bufferAddress(value));
    }

    @BridgeOverride
    public void glUniformMatrix3x2fv(int location, int count, boolean transpose, FloatBuffer value) {
        GLES30.nglUniformMatrix3x2fv(location, count, transpose, bufferAddress(value));
    }

    @BridgeOverride
    public void glUniformMatrix3x4fv(int location, int count, boolean transpose, FloatBuffer value) {
        GLES30.nglUniformMatrix3x4fv(location, count, transpose, bufferAddress(value));
    }

    @BridgeOverride
    public void glUniformMatrix4x2fv(int location, int count, boolean transpose, FloatBuffer value) {
        GLES30.nglUniformMatrix4x2fv(location, count, transpose, bufferAddress(value));
    }

    @BridgeOverride
    public void glUniformMatrix4x3fv(int location, int count, boolean transpose, FloatBuffer value) {
        GLES30.nglUniformMatrix4x3fv(location, count, transpose, bufferAddress(value));
    }

    @BridgeOverride
    public boolean glUnmapBuffer(int target) {
        return GLES30.glUnmapBuffer(target);
    }

    @BridgeOverride
    public void glVertexAttribDivisor(int index, int divisor) {
        GLES30.glVertexAttribDivisor(index, divisor);
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
    public void glVertexAttribIPointer(int index, int size, int type, int stride, int offset) {
        GLES30.glVertexAttribIPointer(index, size, type, stride, offset);
    }
}

