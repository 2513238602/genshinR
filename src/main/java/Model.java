import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static java.lang.Math.*;
import static org.lwjgl.opengl.GL33C.*;

public class Model implements AutoCloseable {
    public static class Part {
        public final Mesh mesh;
        public final Material material;

        public Part(Mesh mesh, Material material) {
            this.mesh = mesh;
            this.material = material;
        }
    }

    public static class Material implements AutoCloseable {
        public final String name;
        public float kdR = 1f;
        public float kdG = 1f;
        public float kdB = 1f;
        private Texture mapKd;

        public Material(String name) {
            this.name = name;
        }

        public boolean hasTexture() {
            return mapKd != null;
        }

        public void setTexture(Texture texture) {
            if (this.mapKd != null) {
                this.mapKd.close();
            }
            this.mapKd = texture;
        }

        public void bindTexture(int unit) {
            if (mapKd != null) {
                mapKd.bind(unit);
            }
        }

        public void unbindTexture() {
            Texture.unbind();
        }

        @Override
        public void close() {
            if (mapKd != null) {
                mapKd.close();
                mapKd = null;
            }
        }
    }

    public static class Mesh implements AutoCloseable {
        private final int vao;
        private final int vbo;
        private final int ebo;
        private final int vertexCount;
        private final boolean indexed;
        private final boolean hasNormal;
        private final boolean hasUV;
        private final float[] aabbMin = new float[3];
        private final float[] aabbMax = new float[3];

        private Mesh(float[] interleaved, int[] indices, boolean hasNormal, boolean hasUV) {
            this.hasNormal = hasNormal;
            this.hasUV = hasUV;
            computeBounds(interleaved, hasNormal, hasUV);

            int strideFloats = 3 + (hasNormal ? 3 : 0) + (hasUV ? 2 : 0);
            int strideBytes = strideFloats * Float.BYTES;

            vao = glGenVertexArrays();
            glBindVertexArray(vao);

            vbo = glGenBuffers();
            glBindBuffer(GL_ARRAY_BUFFER, vbo);
            FloatBuffer fb = BufferUtils.createFloatBuffer(interleaved.length).put(interleaved);
            fb.flip();
            glBufferData(GL_ARRAY_BUFFER, fb, GL_STATIC_DRAW);

            long offset = 0L;
            glVertexAttribPointer(0, 3, GL_FLOAT, false, strideBytes, offset);
            glEnableVertexAttribArray(0);
            offset += 3L * Float.BYTES;

            if (hasNormal) {
                glVertexAttribPointer(1, 3, GL_FLOAT, false, strideBytes, offset);
                glEnableVertexAttribArray(1);
                offset += 3L * Float.BYTES;
            }

            if (hasUV) {
                glVertexAttribPointer(2, 2, GL_FLOAT, false, strideBytes, offset);
                glEnableVertexAttribArray(2);
            }

            if (indices != null && indices.length > 0) {
                indexed = true;
                vertexCount = indices.length;
                ebo = glGenBuffers();
                glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
                IntBuffer ib = BufferUtils.createIntBuffer(indices.length).put(indices);
                ib.flip();
                glBufferData(GL_ELEMENT_ARRAY_BUFFER, ib, GL_STATIC_DRAW);
            } else {
                indexed = false;
                vertexCount = interleaved.length / strideFloats;
                ebo = 0;
            }

            glBindBuffer(GL_ARRAY_BUFFER, 0);
            glBindVertexArray(0);
        }

        private void computeBounds(float[] interleaved, boolean hasNormal, boolean hasUV) {
            int stride = 3 + (hasNormal ? 3 : 0) + (hasUV ? 2 : 0);
            Arrays.fill(aabbMin, Float.POSITIVE_INFINITY);
            Arrays.fill(aabbMax, Float.NEGATIVE_INFINITY);
            for (int i = 0; i < interleaved.length; i += stride) {
                float x = interleaved[i];
                float y = interleaved[i + 1];
                float z = interleaved[i + 2];
                if (x < aabbMin[0]) aabbMin[0] = x;
                if (y < aabbMin[1]) aabbMin[1] = y;
                if (z < aabbMin[2]) aabbMin[2] = z;
                if (x > aabbMax[0]) aabbMax[0] = x;
                if (y > aabbMax[1]) aabbMax[1] = y;
                if (z > aabbMax[2]) aabbMax[2] = z;
            }
        }

        public float[] getCenter() {
            return new float[]{
                    (aabbMin[0] + aabbMax[0]) * 0.5f,
                    (aabbMin[1] + aabbMax[1]) * 0.5f,
                    (aabbMin[2] + aabbMax[2]) * 0.5f
            };
        }

        public float getMaxExtent() {
            return max(aabbMax[0] - aabbMin[0], max(aabbMax[1] - aabbMin[1], aabbMax[2] - aabbMin[2]));
        }

        public float getBoundingRadius() {
            float dx = aabbMax[0] - aabbMin[0];
            float dy = aabbMax[1] - aabbMin[1];
            float dz = aabbMax[2] - aabbMin[2];
            return 0.5f * (float) sqrt(dx * dx + dy * dy + dz * dz);
        }

        public void bind() {
            glBindVertexArray(vao);
            if (ebo != 0) {
                glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
            }
        }

        public void draw() {
            if (indexed) {
                glDrawElements(GL_TRIANGLES, vertexCount, GL_UNSIGNED_INT, 0L);
            } else {
                glDrawArrays(GL_TRIANGLES, 0, vertexCount);
            }
        }

        public void unbind() {
            if (ebo != 0) {
                glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
            }
            glBindVertexArray(0);
        }

        @Override
        public void close() {
            if (ebo != 0) {
                glDeleteBuffers(ebo);
            }
            glDeleteBuffers(vbo);
            glDeleteVertexArrays(vao);
        }

        private static Mesh fromInterleaved(float[] interleaved, int[] indices, boolean hasNormal, boolean hasUV) {
            return new Mesh(interleaved, indices, hasNormal, hasUV);
        }
    }

    public static class Texture implements AutoCloseable {
        private final int id;
        private final int width;
        private final int height;

        private Texture(int id, int width, int height) {
            this.id = id;
            this.width = width;
            this.height = height;
        }

        public static Texture loadResource(String resourcePath, boolean srgb) throws IOException {
            byte[] bytes = readAllBytes(resourcePath);
            ByteBuffer buf = ByteBuffer.allocateDirect(bytes.length);
            buf.put(bytes).flip();

            try (MemoryStack stack = MemoryStack.stackPush()) {
                var w = stack.mallocInt(1);
                var h = stack.mallocInt(1);
                var comp = stack.mallocInt(1);

                STBImage.stbi_set_flip_vertically_on_load(true);
                ByteBuffer img = STBImage.stbi_load_from_memory(buf, w, h, comp, 4);
                if (img == null) {
                    throw new IOException("stbi error: " + STBImage.stbi_failure_reason());
                }

                int tex = glGenTextures();
                glBindTexture(GL_TEXTURE_2D, tex);

                int internal = srgb ? GL_SRGB8_ALPHA8 : GL_RGBA8;
                glTexImage2D(GL_TEXTURE_2D, 0, internal, w.get(0), h.get(0), 0, GL_RGBA, GL_UNSIGNED_BYTE, img);
                glGenerateMipmap(GL_TEXTURE_2D);

                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);

                try {
                    int GL_TEXTURE_MAX_ANISOTROPY_EXT = 0x84FE;
                    int GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT = 0x84FF;
                    float maxAniso = glGetFloat(GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT);
                    glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MAX_ANISOTROPY_EXT, min(4.0f, maxAniso));
                } catch (Throwable ignored) {
                }

                STBImage.stbi_image_free(img);
                glBindTexture(GL_TEXTURE_2D, 0);
                return new Texture(tex, w.get(0), h.get(0));
            }
        }

        private static byte[] readAllBytes(String resPath) throws IOException {
            String p = resPath.startsWith("/") ? resPath.substring(1) : resPath;
            try (InputStream in = ClassLoader.getSystemClassLoader().getResourceAsStream(p)) {
                if (in == null) {
                    throw new IOException("resource not found: " + resPath);
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] tmp = new byte[8192];
                int n;
                while ((n = in.read(tmp)) != -1) {
                    out.write(tmp, 0, n);
                }
                return out.toByteArray();
            }
        }

        public void bind(int unit) {
            glActiveTexture(GL_TEXTURE0 + unit);
            glBindTexture(GL_TEXTURE_2D, id);
        }

        public static void unbind() {
            glBindTexture(GL_TEXTURE_2D, 0);
        }

        @Override
        public void close() {
            glDeleteTextures(id);
        }
    }

    public final List<Part> parts = new ArrayList<>();

    private Model() {
    }

    private void add(Mesh mesh, Material material) {
        parts.add(new Part(mesh, material));
    }

    @Override
    public void close() {
        var uniqueMaterials = new HashSet<Material>();
        for (Part p : parts) {
            p.mesh.close();
            if (p.material != null && uniqueMaterials.add(p.material)) {
                p.material.close();
            }
        }
        parts.clear();
    }

    private record VertexKey(int v, int t, int n) {
    }

    public static Model loadResource(String objResPath, boolean flipV) throws IOException {
        String res = objResPath.startsWith("/") ? objResPath.substring(1) : objResPath;
        InputStream in = ClassLoader.getSystemClassLoader().getResourceAsStream(res);
        if (in == null) {
            throw new FileNotFoundException("resource not found: " + objResPath);
        }

        String baseDir = dirOf(res);
        String objName = res.substring(res.lastIndexOf('/') + 1);
        String baseName = objName.toLowerCase().endsWith(".obj") ? objName.substring(0, objName.length() - 4) : objName;

        ArrayList<float[]> positions = new ArrayList<>();
        ArrayList<float[]> uvs = new ArrayList<>();
        ArrayList<float[]> normals = new ArrayList<>();

        Map<String, Material> materials = new LinkedHashMap<>();
        boolean loadedAnyMTL = false;

        boolean fileHasUV = false;
        boolean fileHasNormal = false;

        class Build {
            LinkedHashMap<VertexKey, Integer> map = new LinkedHashMap<>();
            ArrayList<Float> interleaved = new ArrayList<>();
            ArrayList<Integer> indices = new ArrayList<>();
            boolean seenUV = false;
            boolean seenNormal = false;
        }

        Map<String, Build> builds = new LinkedHashMap<>();
        Build defaultBuild = new Build();
        builds.put("default", defaultBuild);
        materials.put("default", new Material("default"));
        String currentMtl = "default";

        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.strip();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] head = line.split("\\s+", 2);
                String kw = head[0];
                switch (kw) {
                    case "v" -> {
                        String[] p = line.split("\\s+");
                        positions.add(new float[]{f(p[1]), f(p[2]), f(p[3])});
                    }
                    case "vt" -> {
                        String[] p = line.split("\\s+");
                        float u = f(p[1]);
                        float v = f(p[2]);
                        uvs.add(new float[]{u, flipV ? 1f - v : v});
                        fileHasUV = true;
                        for (Build b : builds.values()) {
                            b.seenUV = true;
                        }
                    }
                    case "vn" -> {
                        String[] p = line.split("\\s+");
                        normals.add(new float[]{f(p[1]), f(p[2]), f(p[3])});
                        fileHasNormal = true;
                        for (Build b : builds.values()) {
                            b.seenNormal = true;
                        }
                    }
                    case "mtllib" -> {
                        if (head.length < 2) {
                            break;
                        }
                        String[] toks = head[1].trim().split("\\s+");
                        for (String t : toks) {
                            String ref = normalizeMtlRef(t);
                            if (ref.isEmpty()) {
                                continue;
                            }
                            try {
                                Map<String, Material> mm = loadMtl(baseDir, ref);
                                materials.putAll(mm);
                                if (!mm.isEmpty()) {
                                    loadedAnyMTL = true;
                                }
                            } catch (FileNotFoundException e) {
                                System.err.println("WARN: mtl not found, skip -> " + join(baseDir, ref));
                            }
                        }
                    }
                    case "usemtl" -> {
                        if (head.length < 2) {
                            break;
                        }
                        currentMtl = head[1].trim();
                        Build b = builds.get(currentMtl);
                        if (b == null) {
                            b = new Build();
                            b.seenUV = fileHasUV;
                            b.seenNormal = fileHasNormal;
                            builds.put(currentMtl, b);
                        }
                    }
                    case "f" -> {
                        String[] p = line.split("\\s+");
                        Build b = builds.getOrDefault(currentMtl, defaultBuild);
                        int[] ids = new int[p.length - 1];
                        for (int i = 1; i < p.length; i++) {
                            String[] tri = p[i].split("/");
                            int v = Integer.parseInt(tri[0]);
                            Integer t = (tri.length > 1 && !tri[1].isEmpty()) ? Integer.parseInt(tri[1]) : null;
                            Integer n = (tri.length > 2 && !tri[2].isEmpty()) ? Integer.parseInt(tri[2]) : null;

                            int vi = resolve(v, positions.size());
                            int ti = (t != null) ? resolve(t, uvs.size()) : -1;
                            int ni = (n != null) ? resolve(n, normals.size()) : -1;

                            VertexKey key = new VertexKey(vi, ti, ni);
                            Integer idx = b.map.get(key);
                            if (idx == null) {
                                float[] pos = positions.get(vi);
                                b.interleaved.add(pos[0]);
                                b.interleaved.add(pos[1]);
                                b.interleaved.add(pos[2]);

                                if (ni >= 0) {
                                    float[] nn = normals.get(ni);
                                    b.interleaved.add(nn[0]);
                                    b.interleaved.add(nn[1]);
                                    b.interleaved.add(nn[2]);
                                } else if (b.seenNormal) {
                                    b.interleaved.add(0f);
                                    b.interleaved.add(0f);
                                    b.interleaved.add(1f);
                                }

                                if (ti >= 0) {
                                    float[] tt = uvs.get(ti);
                                    b.interleaved.add(tt[0]);
                                    b.interleaved.add(tt[1]);
                                } else if (b.seenUV) {
                                    b.interleaved.add(0f);
                                    b.interleaved.add(0f);
                                }

                                idx = b.map.size();
                                b.map.put(key, idx);
                            }
                            ids[i - 1] = idx;
                        }

                        for (int i = 1; i + 1 < ids.length; i++) {
                            b.indices.add(ids[0]);
                            b.indices.add(ids[i]);
                            b.indices.add(ids[i + 1]);
                        }
                    }
                    default -> {
                    }
                }
            }
        }

        if (!loadedAnyMTL) {
            String guess = baseName + ".mtl";
            try {
                Map<String, Material> mm = loadMtl(baseDir, guess);
                if (!mm.isEmpty()) {
                    System.out.println("INFO: fallback to " + join(baseDir, guess));
                    materials.putAll(mm);
                }
            } catch (FileNotFoundException ignored) {
            }
        }

        Model model = new Model();
        for (var entry : builds.entrySet()) {
            String mtlName = entry.getKey();
            Build b = entry.getValue();

            float[] inter = new float[b.interleaved.size()];
            for (int i = 0; i < inter.length; i++) {
                inter[i] = b.interleaved.get(i);
            }
            int[] idx = b.indices.stream().mapToInt(Integer::intValue).toArray();

            boolean hasNormal = b.seenNormal;
            boolean hasUV = b.seenUV;

            Mesh mesh = Mesh.fromInterleaved(inter, idx, hasNormal, hasUV);
            Material mat = materials.getOrDefault(mtlName, materials.get("default"));
            model.add(mesh, mat);
        }
        return model;
    }

    private static Map<String, Material> loadMtl(String baseDir, String mtlFile) throws IOException {
        String resPath = join(baseDir, mtlFile);
        InputStream in = ClassLoader.getSystemClassLoader().getResourceAsStream(resPath);
        if (in == null) {
            throw new FileNotFoundException("mtl resource not found: " + resPath);
        }
        Map<String, Material> map = new LinkedHashMap<>();
        Material current = null;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.strip();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] tokens = line.split("\\s+");
                switch (tokens[0]) {
                    case "newmtl" -> {
                        current = new Material(line.substring("newmtl".length()).trim());
                        map.put(current.name, current);
                    }
                    case "Kd" -> {
                        if (current != null && tokens.length >= 4) {
                            current.kdR = f(tokens[1]);
                            current.kdG = f(tokens[2]);
                            current.kdB = f(tokens[3]);
                        }
                    }
                    case "map_Kd" -> {
                        if (current != null) {
                            String tex = tokens[tokens.length - 1];
                            try {
                                current.setTexture(Texture.loadResource(join(baseDir, tex), true));
                            } catch (IOException e) {
                                System.err.println("WARN: texture not found -> " + join(baseDir, tex));
                            }
                        }
                    }
                    default -> {
                    }
                }
            }
        }
        return map;
    }

    private static float f(String s) {
        return Float.parseFloat(s);
    }

    private static int resolve(int idx, int size) {
        return (idx > 0) ? (idx - 1) : (size + idx);
    }

    private static String dirOf(String path) {
        int i = path.lastIndexOf('/');
        return (i >= 0) ? path.substring(0, i + 1) : "";
    }

    private static String join(String base, String file) {
        String b = (base == null) ? "" : base;
        if (!b.isEmpty() && !b.endsWith("/")) {
            b += "/";
        }
        String f = (file == null) ? "" : file;
        while (f.startsWith("/")) {
            f = f.substring(1);
        }
        return b + f;
    }

    private static String normalizeMtlRef(String raw) {
        if (raw == null) {
            return "";
        }
        String r = raw.trim().replace('\\', '/');
        if (r.isEmpty()) {
            return r;
        }
        if (r.contains(" ")) {
            r = r.split("\\s+")[0];
        }
        boolean abs = r.startsWith("/") || r.matches("^[A-Za-z]:/.*") || r.contains(":/");
        if (abs) {
            int i = r.lastIndexOf('/');
            return (i >= 0) ? r.substring(i + 1) : r;
        }
        return r;
    }
}
