import java.util.Arrays;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;   // 3.3 的函数（缓冲、着色器等）
import static org.lwjgl.opengl.GL11C.*;   // 只导常量/函数：glGetString、GL_VENDOR、polygon mode常量


public class MainApp {

    // 改成你的真实资源路径（aserts/ 或 assets/）
    private static final String OBJ_PATH = "asserts/Losalia/Losalia.obj";
    private static final boolean FLIP_V = true; // 若贴图上下颠倒可切换

    public static void main(String[] args) throws Exception {
        Renderer renderer = new Renderer(1280, 800);
        renderer.initWindow("LWJGL – OBJ+MTL Textured Model");

        String vs =
                "#version 330 core\n" +
                        "layout(location=0) in vec3 aPos;\n" +
                        "layout(location=1) in vec3 aNormal;\n" +   // 可选
                        "layout(location=2) in vec2 aUV;\n" +       // 可选
                        "uniform mat4 uMVP;\n" +
                        "out vec2 vUV;\n" +
                        "void main(){ vUV = aUV; gl_Position = uMVP * vec4(aPos,1.0); }\n";

        String fs =
                "#version 330 core\n" +
                        "in vec2 vUV;\n" +
                        "uniform sampler2D uAlbedo;\n" +
                        "uniform int uUseTex;        // 1=用纹理 0=用颜色\n" +
                        "uniform vec3 uColor;        // MTL 的 Kd 或回退色\n" +
                        "out vec4 FragColor;\n" +
                        "void main(){\n" +
                        "  vec3 base = (uUseTex==1) ? texture(uAlbedo, vUV).rgb : uColor;\n" +
                        "  FragColor = vec4(base, 1.0);\n" +
                        "}\n";

        ShaderProgram shader = new ShaderProgram(vs, fs);
        shader.use();
        shader.set1i("uAlbedo", 0);
        ShaderProgram.unbind();

        // ===== 载入 OBJ+MTL+多纹理，按材质切分为多个 Part =====
        Model model = ObjMtlLoader.loadOBJWithMTLResource(OBJ_PATH, FLIP_V);

        // ===== 聚合全局 AABB（用每个子网格的 center+maxExtent 近似合并）=====
        float[] globalMin = { Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY };
        float[] globalMax = { Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY };

        for (Model.Part p : model.parts) {
            float[] c = p.mesh.getCenter();
            float extent = p.mesh.getMaxExtent();
            float half = extent * 0.5f;
            float[] min = { c[0]-half, c[1]-half, c[2]-half };
            float[] max = { c[0]+half, c[1]+half, c[2]+half };
            for (int i=0;i<3;i++){ globalMin[i] = Math.min(globalMin[i], min[i]); }
            for (int i=0;i<3;i++){ globalMax[i] = Math.max(globalMax[i], max[i]); }
        }
        float[] center = {
                (globalMin[0]+globalMax[0])/2f,
                (globalMin[1]+globalMax[1])/2f,
                (globalMin[2]+globalMax[2])/2f
        };
        float dx = globalMax[0]-globalMin[0], dy = globalMax[1]-globalMin[1], dz = globalMax[2]-globalMin[2];
        float radius = 0.5f * (float)Math.sqrt(dx*dx + dy*dy + dz*dz); // 半对角线（安全些）

        System.out.println("Model AABB min=" + Arrays.toString(globalMin) + " max=" + Arrays.toString(globalMax));
        System.out.println("center=" + Arrays.toString(center) + " radius=" + radius);

        // ===== 相机：对准整体模型并按半径取景 =====
        ArcballCamera cam = new ArcballCamera(renderer.getWindow());
        cam.frameMesh(center[0], center[1], center[2], Math.max(1e-6f, radius));
        // 可选：再放宽远裁剪
        cam.setProjection(60f, 0.02f, 1e9f);

        // ===== 线框/退出 快捷键 =====
        final boolean[] wire = { false };
        glfwSetKeyCallback(renderer.getWindow(), (w, key, sc, action, mods) -> {
            if (action == GLFW_PRESS && key == GLFW_KEY_F2) {
                wire[0] = !wire[0];
                glPolygonMode(GL_FRONT_AND_BACK, wire[0] ? GL_LINE : GL_FILL);
            } else if (action == GLFW_PRESS && key == GLFW_KEY_ESCAPE) {
                glfwSetWindowShouldClose(w, true);
            }
        });

        // ===== 主循环 =====
        while (!renderer.shouldClose()) {
            cam.update();
            renderer.beginFrame();

            float[] mvp = Mat4f.multiply(cam.getViewProjection(), Mat4f.identity());
            shader.use();
            shader.setMat4("uMVP", mvp);

            for (Model.Part p : model.parts) {
                if (p.material != null) {
                    shader.set3f("uColor", p.material.kdR, p.material.kdG, p.material.kdB);
                    if (p.material.mapKd != null) {
                        shader.set1i("uUseTex", 1);
                        p.material.mapKd.bind(0);
                    } else {
                        shader.set1i("uUseTex", 0);
                    }
                } else {
                    shader.set1i("uUseTex", 0);
                    shader.set3f("uColor", 0.8f, 0.8f, 0.8f);
                }

                p.mesh.bind();
                p.mesh.draw();
                p.mesh.unbind();
                Texture2D.unbind();
            }

            ShaderProgram.unbind();
            renderer.endFrame();
        }

        // ===== 清理 =====
        model.close();
        shader.dispose();
        renderer.cleanup();
    }
}
