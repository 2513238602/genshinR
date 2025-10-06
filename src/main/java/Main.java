import java.util.Arrays;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL33C.*;

public class Main {
    private static final String OBJ_PATH = "asserts/Losalia/Losalia.obj";
    private static final boolean FLIP_V = false;

    public static void main(String[] args) throws Exception {
        try (Render render = new Render(1280, 800)) {
            render.initWindow("LWJGL – OBJ+MTL Textured Model");

            String vs = "#version 330 core\n" +
                    "layout(location=0) in vec3 aPos;\n" +
                    "layout(location=1) in vec3 aNormal;\n" +
                    "layout(location=2) in vec2 aUV;\n" +
                    "uniform mat4 uMVP;\n" +
                    "out vec2 vUV;\n" +
                    "void main(){ vUV = aUV; gl_Position = uMVP * vec4(aPos,1.0); }\n";

            String fs = "#version 330 core\n" +
                    "in vec2 vUV;\n" +
                    "uniform sampler2D uAlbedo;\n" +
                    "uniform int uUseTex;\n" +
                    "uniform vec3 uColor;\n" +
                    "out vec4 FragColor;\n" +
                    "void main(){\n" +
                    "  vec3 base = (uUseTex==1) ? texture(uAlbedo, vUV).rgb : uColor;\n" +
                    "  FragColor = vec4(base, 1.0);\n" +
                    "}\n";

            try (Shader shader = new Shader(vs, fs)) {
                shader.use();
                shader.set1i("uAlbedo", 0);
                Shader.unbind();

                Model model = Model.loadResource(OBJ_PATH, FLIP_V);

                float[] globalMin = {Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY};
                float[] globalMax = {Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};

                for (Model.Part part : model.parts) {
                    float[] c = part.mesh.getCenter();
                    float extent = part.mesh.getMaxExtent();
                    float half = extent * 0.5f;
                    float[] min = {c[0] - half, c[1] - half, c[2] - half};
                    float[] max = {c[0] + half, c[1] + half, c[2] + half};
                    for (int i = 0; i < 3; i++) {
                        globalMin[i] = Math.min(globalMin[i], min[i]);
                        globalMax[i] = Math.max(globalMax[i], max[i]);
                    }
                }

                float[] center = {
                        (globalMin[0] + globalMax[0]) / 2f,
                        (globalMin[1] + globalMax[1]) / 2f,
                        (globalMin[2] + globalMax[2]) / 2f
                };
                float dx = globalMax[0] - globalMin[0];
                float dy = globalMax[1] - globalMin[1];
                float dz = globalMax[2] - globalMin[2];
                float radius = 0.5f * (float) Math.sqrt(dx * dx + dy * dy + dz * dz);

                System.out.println("Model AABB min=" + Arrays.toString(globalMin) + " max=" + Arrays.toString(globalMax));
                System.out.println("center=" + Arrays.toString(center) + " radius=" + radius);

                Camera camera = new Camera(render.getWindow());
                camera.frame(center[0], center[1], center[2], Math.max(1e-6f, radius));
                camera.setProjection(60f, 0.02f, 1e9f);

                final boolean[] wire = {false};
                glfwSetKeyCallback(render.getWindow(), (w, key, sc, action, mods) -> {
                    if (action == GLFW_PRESS && key == GLFW_KEY_F2) {
                        wire[0] = !wire[0];
                        glPolygonMode(GL_FRONT_AND_BACK, wire[0] ? GL_LINE : GL_FILL);
                    } else if (action == GLFW_PRESS && key == GLFW_KEY_ESCAPE) {
                        glfwSetWindowShouldClose(w, true);
                    }
                });

                while (!render.shouldClose()) {
                    camera.update();
                    render.beginFrame();

                    float[] mvp = camera.getViewProjection();
                    shader.use();
                    shader.setMat4("uMVP", mvp);

                    for (Model.Part part : model.parts) {
                        Model.Material material = part.material;
                        if (material != null) {
                            shader.set3f("uColor", material.kdR, material.kdG, material.kdB);
                            if (material.hasTexture()) {
                                shader.set1i("uUseTex", 1);
                                material.bindTexture(0);
                            } else {
                                shader.set1i("uUseTex", 0);
                            }
                        } else {
                            shader.set1i("uUseTex", 0);
                            shader.set3f("uColor", 0.8f, 0.8f, 0.8f);
                        }

                        part.mesh.bind();
                        part.mesh.draw();
                        part.mesh.unbind();
                        Model.Texture.unbind();
                    }

                    Shader.unbind();
                    render.endFrame();
                }

                model.close();
            }
        }
    }
}
