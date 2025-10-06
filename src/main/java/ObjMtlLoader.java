import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ObjMtlLoader {

    private record VertexKey(int v, int t, int n) {}
    private static float f(String s){ return Float.parseFloat(s); }
    private static int resolve(int idx,int size){ return (idx>0)?(idx-1):(size+idx); }
    private static String dirOf(String path){
        int i = path.lastIndexOf('/');
        return (i>=0)? path.substring(0,i+1) : "";
    }
    private static String join(String base, String file){
        String b = (base==null)?"":base;
        if (!b.isEmpty() && !b.endsWith("/")) b += "/";
        String f = (file==null)?"":file;
        while (f.startsWith("/")) f = f.substring(1);
        return b + f;
    }
    /** 清洗 mtllib 引用：绝对/奇怪路径只取文件名；相对路径保留 */
    private static String normalizeMtlRef(String raw){
        if (raw == null) return "";
        String r = raw.trim().replace('\\','/');
        if (r.isEmpty()) return r;
        if (r.contains(" ")) r = r.split("\\s+")[0]; // 去掉多余参数
        boolean abs = r.startsWith("/") || r.matches("^[A-Za-z]:/.*") || r.contains(":/");
        if (abs) {
            int i = r.lastIndexOf('/');
            return (i>=0)? r.substring(i+1) : r;
        }
        return r;
    }

    public static Model loadOBJWithMTLResource(String objResPath, boolean flipV) throws IOException {
        String res = objResPath.startsWith("/") ? objResPath.substring(1) : objResPath;
        InputStream in = ClassLoader.getSystemClassLoader().getResourceAsStream(res);
        if (in == null) throw new FileNotFoundException("resource not found: " + objResPath);

        String baseDir  = dirOf(res);                         // e.g. asserts/Losalia/
        String objName  = res.substring(res.lastIndexOf('/')+1);
        String baseName = objName.toLowerCase().endsWith(".obj") ? objName.substring(0, objName.length()-4) : objName;

        // 顶点池
        ArrayList<float[]> pos = new ArrayList<>();
        ArrayList<float[]> uv  = new ArrayList<>();
        ArrayList<float[]> nrm = new ArrayList<>();

        // 材质库
        Map<String, Material> materials = new LinkedHashMap<>();
        boolean loadedAnyMTL = false;

        // 文件级全局标记（关键：保证所有子网格 stride 一致）
        boolean fileHasUV = false;
        boolean fileHasN  = false;

        // 每材质累积器
        class Build {
            LinkedHashMap<VertexKey,Integer> map = new LinkedHashMap<>();
            ArrayList<Float>  inter   = new ArrayList<>();
            ArrayList<Integer> indices = new ArrayList<>();
            boolean seenUV=false, seenN=false;
        }
        Map<String, Build> builds = new LinkedHashMap<>();
        String currentMtl = "default";
        builds.put(currentMtl, new Build());
        materials.put("default", null);

        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.strip();
                if (line.isEmpty() || line.startsWith("#")) continue;

                String[] head = line.split("\\s+", 2);
                String kw = head[0];

                switch (kw) {
                    case "v" -> {
                        String[] p = line.split("\\s+");
                        pos.add(new float[]{ f(p[1]), f(p[2]), f(p[3]) });
                    }
                    case "vt" -> {
                        String[] p = line.split("\\s+");
                        float u=f(p[1]); float v=f(p[2]);
                        uv.add(new float[]{ u, flipV?1f-v:v });
                        fileHasUV = true;
                        for (Build b: builds.values()) b.seenUV = true; // 同步已有
                    }
                    case "vn" -> {
                        String[] p = line.split("\\s+");
                        nrm.add(new float[]{ f(p[1]), f(p[2]), f(p[3]) });
                        fileHasN = true;
                        for (Build b: builds.values()) b.seenN = true;  // 同步已有
                    }
                    case "mtllib" -> {
                        if (head.length < 2) break;
                        String[] toks = head[1].trim().split("\\s+");
                        for (String t : toks) {
                            String ref = normalizeMtlRef(t);
                            if (ref.isEmpty()) continue;
                            try {
                                Map<String, Material> mm = MtlLoader.loadResource(baseDir, ref);
                                materials.putAll(mm);
                                if (!mm.isEmpty()) loadedAnyMTL = true;
                            } catch (FileNotFoundException e) {
                                System.err.println("WARN: mtl not found, skip -> " + join(baseDir, ref));
                            }
                        }
                    }
                    case "usemtl" -> {
                        if (head.length < 2) break;
                        currentMtl = head[1].trim();
                        // 显式 get/put，避免 lambda 的 effectively-final 限制
                        Build nb = builds.get(currentMtl);
                        if (nb == null) {
                            nb = new Build();
                            nb.seenUV = fileHasUV; // 继承文件级标记
                            nb.seenN  = fileHasN;
                            builds.put(currentMtl, nb);
                        }
                    }
                    case "f" -> {
                        String[] p = line.split("\\s+");
                        Build b = builds.get(currentMtl);
                        int[] ids = new int[p.length-1];

                        for (int i=1;i<p.length;i++){
                            String[] tri = p[i].split("/");
                            int v = Integer.parseInt(tri[0]);
                            Integer t = (tri.length>1 && !tri[1].isEmpty()) ? Integer.parseInt(tri[1]) : null;
                            Integer n = (tri.length>2 && !tri[2].isEmpty()) ? Integer.parseInt(tri[2]) : null;

                            int vi = resolve(v, pos.size());
                            int ti = (t!=null)? resolve(t, uv.size())  : -1;
                            int ni = (n!=null)? resolve(n, nrm.size()) : -1;

                            VertexKey key = new VertexKey(vi, ti, ni);
                            Integer idx = b.map.get(key);
                            if (idx == null) {
                                float[] pp = pos.get(vi);
                                b.inter.add(pp[0]); b.inter.add(pp[1]); b.inter.add(pp[2]);

                                if (ni>=0){
                                    float[] nn=nrm.get(ni);
                                    b.inter.add(nn[0]); b.inter.add(nn[1]); b.inter.add(nn[2]);
                                } else if (b.seenN){
                                    b.inter.add(0f); b.inter.add(0f); b.inter.add(1f);
                                }

                                if (ti>=0){
                                    float[] tt=uv.get(ti);
                                    b.inter.add(tt[0]); b.inter.add(tt[1]);
                                } else if (b.seenUV){
                                    b.inter.add(0f); b.inter.add(0f);
                                }

                                idx = b.map.size();
                                b.map.put(key, idx);
                            }
                            ids[i-1] = idx;
                        }
                        // n-gon 扇形三角化
                        for (int i=1;i+1<ids.length;i++){
                            b.indices.add(ids[0]); b.indices.add(ids[i]); b.indices.add(ids[i+1]);
                        }
                    }
                    default -> { /* ignore others */ }
                }
            }
        }

        // 若没加载到任何 mtl，尝试 <obj同名>.mtl
        if (!loadedAnyMTL) {
            String guess = baseName + ".mtl";
            try {
                Map<String, Material> mm = MtlLoader.loadResource(baseDir, guess);
                if (!mm.isEmpty()) {
                    System.out.println("INFO: fallback to " + join(baseDir, guess));
                    materials.putAll(mm);
                }
            } catch (FileNotFoundException ignored) {}
        }

        // 生成 Model
        Model model = new Model();
        for (var e : builds.entrySet()){
            String mtlName = e.getKey();
            Build b = e.getValue();

            float[] inter = new float[b.inter.size()];
            for (int i=0;i<inter.length;i++) inter[i]=b.inter.get(i);
            int[] idx = b.indices.stream().mapToInt(Integer::intValue).toArray();

            boolean hasN  = b.seenN;
            boolean hasUV = b.seenUV;

            Mesh mesh = Mesh.fromInterleaved(inter, idx, hasN, hasUV);
            Material mat = materials.getOrDefault(mtlName, null);
            model.add(mesh, mat);
        }
        return model;
    }
}
