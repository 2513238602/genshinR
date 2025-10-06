
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL33C.*;
import static java.lang.Math.*;

public class Texture2D implements AutoCloseable {
    public final int id;
    public final int width, height;
    public final boolean hasAlpha;

    private Texture2D(int id, int w, int h, boolean a){ this.id=id; this.width=w; this.height=h; this.hasAlpha=a; }

    public static Texture2D loadResource(String resPath, boolean srgb) throws IOException {
        byte[] bytes = readAllBytes(resPath);
        ByteBuffer buf = ByteBuffer.allocateDirect(bytes.length);
        buf.put(bytes).flip();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            var w = stack.mallocInt(1);
            var h = stack.mallocInt(1);
            var comp = stack.mallocInt(1);

            STBImage.stbi_set_flip_vertically_on_load(false); // 我们在 OBJ 已经可选 flipV
            ByteBuffer img = STBImage.stbi_load_from_memory(buf, w, h, comp, 4); // 强制 RGBA
            if (img == null) throw new IOException("stbi error: " + STBImage.stbi_failure_reason());

            int tex = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, tex);

            int internal = srgb ? GL_SRGB8_ALPHA8 : GL_RGBA8;
            glTexImage2D(GL_TEXTURE_2D, 0, internal, w.get(0), h.get(0), 0, GL_RGBA, GL_UNSIGNED_BYTE, img);
            glGenerateMipmap(GL_TEXTURE_2D);

            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);

            // 可选各向异性
            float[] maxAniso = new float[1];
            int GL_TEXTURE_MAX_ANISOTROPY_EXT = 0x84FE;
            int GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT = 0x84FF;
            try {
                maxAniso[0] = glGetFloat(GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT);
                glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MAX_ANISOTROPY_EXT, min(4.0f, maxAniso[0]));
            } catch (Throwable ignored){}

            STBImage.stbi_image_free(img);
            glBindTexture(GL_TEXTURE_2D, 0);
            return new Texture2D(tex, w.get(0), h.get(0), true);
        }
    }

    private static byte[] readAllBytes(String resPath) throws IOException {
        String p = resPath.startsWith("/") ? resPath.substring(1) : resPath;
        try (InputStream in = ClassLoader.getSystemClassLoader().getResourceAsStream(p)) {
            if (in == null) throw new IOException("resource not found: " + resPath);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] tmp = new byte[8192];
            int n; while ((n = in.read(tmp)) != -1) out.write(tmp, 0, n);
            return out.toByteArray();
        }
    }

    public void bind(int unit){ glActiveTexture(GL_TEXTURE0 + unit); glBindTexture(GL_TEXTURE_2D, id); }
    public static void unbind(){ glBindTexture(GL_TEXTURE_2D, 0); }
    @Override public void close(){ glDeleteTextures(id); }
}
