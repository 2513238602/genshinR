import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL33C.*;

public class Shader implements AutoCloseable {
    private final int programId;

    public Shader(String vertexSrc, String fragmentSrc) {
        int vs = compile(GL_VERTEX_SHADER, vertexSrc);
        int fs = compile(GL_FRAGMENT_SHADER, fragmentSrc);
        programId = glCreateProgram();
        glAttachShader(programId, vs);
        glAttachShader(programId, fs);
        glLinkProgram(programId);

        if (glGetProgrami(programId, GL_LINK_STATUS) == GL_FALSE) {
            String log = glGetProgramInfoLog(programId);
            throw new IllegalStateException("Program link failed:\n" + log);
        }
        glDeleteShader(vs);
        glDeleteShader(fs);
    }

    private static int compile(int type, String src) {
        int id = glCreateShader(type);
        glShaderSource(id, src);
        glCompileShader(id);
        if (glGetShaderi(id, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(id);
            throw new IllegalStateException(
                    (type == GL_VERTEX_SHADER ? "Vertex" : "Fragment") + " shader compile failed:\n" + log);
        }
        return id;
    }

    public void use() {
        glUseProgram(programId);
    }

    public static void unbind() {
        glUseProgram(0);
    }

    public void set1i(String name, int v) {
        glUniform1i(getLocation(name), v);
    }

    public void set3f(String name, float x, float y, float z) {
        glUniform3f(getLocation(name), x, y, z);
    }

    public void setMat4(String name, float[] m16) {
        if (m16.length != 16) {
            throw new IllegalArgumentException("mat4 requires 16 floats");
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer fb = stack.mallocFloat(16);
            fb.put(m16).flip();
            glUniformMatrix4fv(getLocation(name), false, fb);
        }
    }

    private int getLocation(String name) {
        int loc = glGetUniformLocation(programId, name);
        if (loc < 0) {
            throw new IllegalArgumentException("Uniform not found: " + name);
        }
        return loc;
    }

    @Override
    public void close() {
        glDeleteProgram(programId);
    }
}
