import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class ObjReader {

    public static final class Result {
        public final float[] interleaved;
        public final int[] indices;
        public final boolean hasNormal;
        public final boolean hasUV;
        public Result(float[] interleaved, int[] indices, boolean hasNormal, boolean hasUV) {
            this.interleaved = interleaved; this.indices = indices;
            this.hasNormal = hasNormal; this.hasUV = hasUV;
        }
    }

    // ---- 对外入口：磁盘路径 ----
    public static Result read(Path path, boolean flipV) throws IOException {
        try (BufferedReader br = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return read(br, flipV);
        }
    }

    // ---- 对外入口：classpath 资源 ----
    public static Result readResource(String resourcePath, boolean flipV) throws IOException {
        if (resourcePath.startsWith("/")) resourcePath = resourcePath.substring(1);
        InputStream in = ObjReader.class.getClassLoader().getResourceAsStream(resourcePath);
        if (in == null) throw new FileNotFoundException("resource not found: " + resourcePath);
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            return read(br, flipV);
        }
    }

    // ---- 内部通用解析 ----
    private static Result read(BufferedReader br, boolean flipV) throws IOException {
        ArrayList<float[]> pos = new ArrayList<>();
        ArrayList<float[]> uv  = new ArrayList<>();
        ArrayList<float[]> nrm = new ArrayList<>();
        LinkedHashMap<VertexKey, Integer> map = new LinkedHashMap<>();
        ArrayList<Float> interleaved = new ArrayList<>();
        ArrayList<Integer> indices = new ArrayList<>();
        boolean seenUV = false, seenN = false;

        String line;
        while ((line = br.readLine()) != null) {
            line = line.strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] p = line.split("\\s+");
            switch (p[0]) {
                case "v"  -> pos.add(new float[]{f(p[1]), f(p[2]), f(p[3])});
                case "vt" -> { float u=f(p[1]); float v=f(p[2]); uv.add(new float[]{u, flipV?1f-v:v}); seenUV=true; }
                case "vn" -> { nrm.add(new float[]{f(p[1]), f(p[2]), f(p[3])}); seenN=true; }
                case "f"  -> {
                    int[] ids = new int[p.length-1];
                    for (int i=1;i<p.length;i++) ids[i-1]=getOrCreate(p[i],pos,uv,nrm,map,interleaved,seenUV,seenN);
                    for (int i=1;i+1<ids.length;i++){ indices.add(ids[0]); indices.add(ids[i]); indices.add(ids[i+1]); }
                }
                default -> {}
            }
        }
        boolean hasUV=false, hasN=false;
        for (VertexKey k: map.keySet()){ hasUV|=k.t>=0; hasN|=k.n>=0; }
        hasUV|=seenUV; hasN|=seenN;

        float[] vtx = new float[interleaved.size()];
        for (int i=0;i<vtx.length;i++) vtx[i]=interleaved.get(i);
        int[] idx = indices.stream().mapToInt(Integer::intValue).toArray();
        return new Result(vtx, idx, hasN, hasUV);
    }

    private record VertexKey(int v,int t,int n){}
    private static float f(String s){ return Float.parseFloat(s); }
    private static int resolve(int idx,int size){ return (idx>0)?(idx-1):(size+idx); }

    private static int getOrCreate(
            String token, ArrayList<float[]> pos, ArrayList<float[]> uv, ArrayList<float[]> nrm,
            LinkedHashMap<VertexKey,Integer> map, ArrayList<Float> out, boolean seenUV, boolean seenN) {

        String[] tri = token.split("/");
        int v = Integer.parseInt(tri[0]);
        Integer t = (tri.length>1 && !tri[1].isEmpty()) ? Integer.parseInt(tri[1]) : null;
        Integer n = (tri.length>2 && !tri[2].isEmpty()) ? Integer.parseInt(tri[2]) : null;

        int vi = resolve(v, pos.size());
        int ti = (t!=null)? resolve(t, uv.size())  : -1;
        int ni = (n!=null)? resolve(n, nrm.size()) : -1;

        VertexKey key = new VertexKey(vi, ti, ni);
        Integer idx = map.get(key);
        if (idx != null) return idx;

        float[] p = pos.get(vi);
        out.add(p[0]); out.add(p[1]); out.add(p[2]);

        if (ni>=0){ float[] nn=nrm.get(ni); out.add(nn[0]); out.add(nn[1]); out.add(nn[2]); }
        else if (seenN){ out.add(0f); out.add(0f); out.add(1f); }

        if (ti>=0){ float[] tt=uv.get(ti); out.add(tt[0]); out.add(tt[1]); }
        else if (seenUV){ out.add(0f); out.add(0f); }

        int newIdx = map.size();
        map.put(key, newIdx);
        return newIdx;
    }
}