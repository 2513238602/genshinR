import java.util.ArrayList;
import java.util.List;

public class Model implements AutoCloseable {
    public static class Part {
        public final Mesh mesh;
        public final Material material;
        public Part(Mesh m, Material mat){ this.mesh=m; this.material=mat; }
    }
    public final List<Part> parts = new ArrayList<>();

    public void add(Mesh m, Material mat){ parts.add(new Part(m, mat)); }

    @Override public void close() {
        for (Part p: parts){ p.mesh.dispose(); if (p.material != null) p.material.close(); }
        parts.clear();
    }
}
