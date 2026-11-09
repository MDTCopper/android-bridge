package copper.bridge.v5;

import copper.bridge.annotation.BridgeInterface;

import io.anuke.arc.graphics.*;
import java.nio.*;
import org.lwjgl.opengles.*;
import org.lwjgl.system.*;

/**
 * JVM-side implementation of arc's OpenGL ES 2.0 interface. The engine runs in a JVM hosted by ART, so {@code
 * android.opengl} is unreachable and LWJGL's OpenGL ES bindings are the only route to GL.
 */
public class BridgeGL20 implements GL20 {

    protected static long bufferAddress(Buffer buffer) {
        if (buffer == null) {
            return 0L;
        }
        if (buffer instanceof ByteBuffer || buffer instanceof ShortBuffer || buffer instanceof IntBuffer || buffer instanceof FloatBuffer) {
            return MemoryUtil.memAddress(buffer);
        }
        throw new IllegalArgumentException("Unsupported buffer type for GL data: " + buffer.getClass().getName());
    }

    @BridgeInterface
    public void glActiveTexture(int texture) {
        GLES20.glActiveTexture(texture);
    }

    @BridgeInterface
    public void glBindTexture(int target, int texture) {
        GLES20.glBindTexture(target, texture);
    }

    @BridgeInterface
    public void glBlendFunc(int sfactor, int dfactor) {
        GLES20.glBlendFunc(sfactor, dfactor);
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
    public void glCullFace(int mode) {
        GLES20.glCullFace(mode);
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
    public void glDisable(int cap) {
        GLES20.glDisable(cap);
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
    public void glEnable(int cap) {
        GLES20.glEnable(cap);
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
    public void glFrontFace(int mode) {
        GLES20.glFrontFace(mode);
    }

    @BridgeInterface
    public int glGenTexture() {
        return GLES20.glGenTextures();
    }

    @BridgeInterface
    public int glGetError() {
        return GLES20.glGetError();
    }

    @BridgeInterface
    public void glGetIntegerv(int pname, IntBuffer params) {
        GLES20.glGetIntegerv(pname, params);
    }

    @BridgeInterface
    public String glGetString(int name) {
        return GLES20.glGetString(name);
    }

    @BridgeInterface
    public void glHint(int target, int mode) {
        GLES20.glHint(target, mode);
    }

    @BridgeInterface
    public void glLineWidth(float width) {
        GLES20.glLineWidth(width);
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
    public void glScissor(int x, int y, int width, int height) {
        GLES20.glScissor(x, y, width, height);
    }

    @BridgeInterface
    public void glStencilFunc(int func, int ref, int mask) {
        GLES20.glStencilFunc(func, ref, mask);
    }

    @BridgeInterface
    public void glStencilMask(int mask) {
        GLES20.glStencilMask(mask);
    }

    @BridgeInterface
    public void glStencilOp(int fail, int zfail, int zpass) {
        GLES20.glStencilOp(fail, zfail, zpass);
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
    public void glTexSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, Buffer pixels) {
        GLES20.glTexSubImage2D(target, level, xoffset, yoffset, width, height, format, type, bufferAddress(pixels));
    }

    @BridgeInterface
    public void glViewport(int x, int y, int width, int height) {
        GLES20.glViewport(x, y, width, height);
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
    public void glBlendFuncSeparate(int srcRGB, int dstRGB, int srcAlpha, int dstAlpha) {
        GLES20.glBlendFuncSeparate(srcRGB, dstRGB, srcAlpha, dstAlpha);
    }

    @BridgeInterface
    public void glBufferData(int target, int size, Buffer data, int usage) {
        if (data == null) {
            GLES20.glBufferData(target, (long)size, usage);
        } else {
            GLES20.nglBufferData(target, (long)size, bufferAddress(data), usage);
        }
    }

    @BridgeInterface
    public void glBufferSubData(int target, int offset, int size, Buffer data) {
        GLES20.nglBufferSubData(target, (long)offset, (long)size, bufferAddress(data));
    }

    @BridgeInterface
    public int glCheckFramebufferStatus(int target) {
        return GLES20.glCheckFramebufferStatus(target);
    }

    @BridgeInterface
    public void glCompileShader(int shader) {
        GLES20.glCompileShader(shader);
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
    public void glDetachShader(int program, int shader) {
        GLES20.glDetachShader(program, shader);
    }

    @BridgeInterface
    public void glDisableVertexAttribArray(int index) {
        GLES20.glDisableVertexAttribArray(index);
    }

    @BridgeInterface
    public void glDrawElements(int mode, int count, int type, int indices) {
        GLES20.glDrawElements(mode, count, type, (long)indices);
    }

    @BridgeInterface
    public void glEnableVertexAttribArray(int index) {
        GLES20.glEnableVertexAttribArray(index);
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
    public void glGetFloatv(int pname, FloatBuffer params) {
        GLES20.glGetFloatv(pname, params);
    }

    @BridgeInterface
    public void glGetFramebufferAttachmentParameteriv(int target, int attachment, int pname, IntBuffer params) {
        GLES20.glGetFramebufferAttachmentParameteriv(target, attachment, pname, params);
    }

    @BridgeInterface
    public void glGetProgramiv(int program, int pname, IntBuffer params) {
        GLES20.glGetProgramiv(program, pname, params);
    }

    @BridgeInterface
    public String glGetProgramInfoLog(int program) {
        return GLES20.glGetProgramInfoLog(program);
    }

    @BridgeInterface
    public void glGetRenderbufferParameteriv(int target, int pname, IntBuffer params) {
        GLES20.glGetRenderbufferParameteriv(target, pname, params);
    }

    @BridgeInterface
    public void glGetShaderiv(int shader, int pname, IntBuffer params) {
        GLES20.glGetShaderiv(shader, pname, params);
    }

    @BridgeInterface
    public String glGetShaderInfoLog(int shader) {
        return GLES20.glGetShaderInfoLog(shader);
    }

    @BridgeInterface
    public void glGetShaderPrecisionFormat(int shadertype, int precisiontype, IntBuffer range, IntBuffer precision) {
        GLES20.glGetShaderPrecisionFormat(shadertype, precisiontype, range, precision);
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
    public void glLinkProgram(int program) {
        GLES20.glLinkProgram(program);
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
    public void glShaderSource(int shader, String string) {
        GLES20.glShaderSource(shader, string);
    }

    @BridgeInterface
    public void glStencilFuncSeparate(int face, int func, int ref, int mask) {
        GLES20.glStencilFuncSeparate(face, func, ref, mask);
    }

    @BridgeInterface
    public void glStencilMaskSeparate(int face, int mask) {
        GLES20.glStencilMaskSeparate(face, mask);
    }

    @BridgeInterface
    public void glStencilOpSeparate(int face, int fail, int zfail, int zpass) {
        GLES20.glStencilOpSeparate(face, fail, zfail, zpass);
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
    public void glUniform1f(int location, float x) {
        GLES20.glUniform1f(location, x);
    }

    @BridgeInterface
    public void glUniform1fv(int location, int count, FloatBuffer v) {
        GLES20.nglUniform1fv(location, count, bufferAddress(v));
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
    public void glUniform1i(int location, int x) {
        GLES20.glUniform1i(location, x);
    }

    @BridgeInterface
    public void glUniform1iv(int location, int count, IntBuffer v) {
        GLES20.nglUniform1iv(location, count, bufferAddress(v));
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
    public void glUniform2f(int location, float x, float y) {
        GLES20.glUniform2f(location, x, y);
    }

    @BridgeInterface
    public void glUniform2fv(int location, int count, FloatBuffer v) {
        GLES20.nglUniform2fv(location, count, bufferAddress(v));
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
    public void glUniform2i(int location, int x, int y) {
        GLES20.glUniform2i(location, x, y);
    }

    @BridgeInterface
    public void glUniform2iv(int location, int count, IntBuffer v) {
        GLES20.nglUniform2iv(location, count, bufferAddress(v));
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
    public void glUniform3f(int location, float x, float y, float z) {
        GLES20.glUniform3f(location, x, y, z);
    }

    @BridgeInterface
    public void glUniform3fv(int location, int count, FloatBuffer v) {
        GLES20.nglUniform3fv(location, count, bufferAddress(v));
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
    public void glUniform3i(int location, int x, int y, int z) {
        GLES20.glUniform3i(location, x, y, z);
    }

    @BridgeInterface
    public void glUniform3iv(int location, int count, IntBuffer v) {
        GLES20.nglUniform3iv(location, count, bufferAddress(v));
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
    public void glUniform4f(int location, float x, float y, float z, float w) {
        GLES20.glUniform4f(location, x, y, z, w);
    }

    @BridgeInterface
    public void glUniform4fv(int location, int count, FloatBuffer v) {
        GLES20.nglUniform4fv(location, count, bufferAddress(v));
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
    public void glUniform4i(int location, int x, int y, int z, int w) {
        GLES20.glUniform4i(location, x, y, z, w);
    }

    @BridgeInterface
    public void glUniform4iv(int location, int count, IntBuffer v) {
        GLES20.nglUniform4iv(location, count, bufferAddress(v));
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
    public void glUniformMatrix2fv(int location, int count, boolean transpose, FloatBuffer value) {
        GLES20.nglUniformMatrix2fv(location, count, transpose, bufferAddress(value));
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
    public void glUniformMatrix3fv(int location, int count, boolean transpose, FloatBuffer value) {
        GLES20.nglUniformMatrix3fv(location, count, transpose, bufferAddress(value));
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
    public void glUniformMatrix4fv(int location, int count, boolean transpose, FloatBuffer value) {
        GLES20.nglUniformMatrix4fv(location, count, transpose, bufferAddress(value));
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
    public void glVertexAttribPointer(int indx, int size, int type, boolean normalized, int stride, Buffer ptr) {
        GLES20.glVertexAttribPointer(indx, size, type, normalized, stride, bufferAddress(ptr));
    }

    @BridgeInterface
    public void glVertexAttribPointer(int indx, int size, int type, boolean normalized, int stride, int ptr) {
        GLES20.glVertexAttribPointer(indx, size, type, normalized, stride, (long)ptr);
    }
}