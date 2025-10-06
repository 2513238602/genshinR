import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;
import static org.lwjgl.opengl.GL41.glClearDepthf;
import static org.lwjgl.system.MemoryUtil.NULL;

public class Render implements AutoCloseable {
    private long window = NULL;
    private int width;
    private int height;
    private float clearR = 0.12f;
    private float clearG = 0.13f;
    private float clearB = 0.15f;
    private float clearA = 1.0f;

    public Render(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public void initWindow(String title) {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

        window = glfwCreateWindow(width, height, title, NULL, NULL);
        if (window == NULL) {
            throw new RuntimeException("Failed to create GLFW window");
        }

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);

        GL.createCapabilities();

        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LESS);
        glClearDepthf(1.0f);
        glDisable(GL_CULL_FACE);

        glViewport(0, 0, width, height);
        glfwSetFramebufferSizeCallback(window, (win, w, h) -> {
            width = Math.max(1, w);
            height = Math.max(1, h);
            glViewport(0, 0, width, height);
        });
    }

    public long getWindow() {
        return window;
    }

    public void setClearColor(float r, float g, float b, float a) {
        clearR = r;
        clearG = g;
        clearB = b;
        clearA = a;
    }

    public void beginFrame() {
        glClearColor(clearR, clearG, clearB, clearA);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    }

    public void endFrame() {
        glfwSwapBuffers(window);
        glfwPollEvents();
    }

    public boolean shouldClose() {
        return glfwWindowShouldClose(window);
    }

    public void requestClose() {
        glfwSetWindowShouldClose(window, true);
    }

    @Override
    public void close() {
        if (window != NULL) {
            glfwDestroyWindow(window);
            window = NULL;
        }
        glfwTerminate();
        GLFWErrorCallback cb = glfwSetErrorCallback(null);
        if (cb != null) {
            cb.free();
        }
    }
}
