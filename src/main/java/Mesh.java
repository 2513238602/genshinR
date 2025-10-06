import org.lwjgl.BufferUtils;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.file.Path;
import static org.lwjgl.opengl.GL33C.*;

public class Mesh implements AutoCloseable {
    private final int vao, vbo, ebo;
    private final int vertexCount;
    private final boolean indexed;
    private final boolean hasNormal, hasUV;
    // Mesh.java 增加字段与方法
    private final float[] aabbMin = new float[3];
    private final float[] aabbMax = new float[3];

    private void computeBounds(float[] interleaved, boolean hasNormal, boolean hasUV){
        int stride = 3 + (hasNormal?3:0) + (hasUV?2:0);
        aabbMin[0]=aabbMin[1]=aabbMin[2]= Float.POSITIVE_INFINITY;
        aabbMax[0]=aabbMax[1]=aabbMax[2]= Float.NEGATIVE_INFINITY;
        for (int i=0;i<interleaved.length;i+=stride){
            float x=interleaved[i], y=interleaved[i+1], z=interleaved[i+2];
            if (x<aabbMin[0]) aabbMin[0]=x; if (x>aabbMax[0]) aabbMax[0]=x;
            if (y<aabbMin[1]) aabbMin[1]=y; if (y>aabbMax[1]) aabbMax[1]=y;
            if (z<aabbMin[2]) aabbMin[2]=z; if (z>aabbMax[2]) aabbMax[2]=z;
        }
    }
    public float[] getCenter(){ return new float[]{ (aabbMin[0]+aabbMax[0])/2f, (aabbMin[1]+aabbMax[1])/2f, (aabbMin[2]+aabbMax[2])/2f}; }
    public float getMaxExtent(){ return Math.max(aabbMax[0]-aabbMin[0], Math.max(aabbMax[1]-aabbMin[1], aabbMax[2]-aabbMin[2])); }

    public float getBoundingRadius() {
        float dx = aabbMax[0] - aabbMin[0];
        float dy = aabbMax[1] - aabbMin[1];
        float dz = aabbMax[2] - aabbMin[2];
        return 0.5f * (float) Math.sqrt(dx*dx + dy*dy + dz*dz); // 半对角线
    }


    private Mesh(float[] interleaved, int[] indices, boolean hasNormal, boolean hasUV) {

        this.hasNormal = hasNormal; this.hasUV = hasUV;

        // ★ 必须：先算 AABB，后面相机会用到
        computeBounds(interleaved, hasNormal, hasUV);

        int strideFloats = 3 + (hasNormal?3:0) + (hasUV?2:0);
        int strideBytes  = strideFloats * Float.BYTES;

        vao = glGenVertexArrays(); glBindVertexArray(vao);
        vbo = glGenBuffers(); glBindBuffer(GL_ARRAY_BUFFER, vbo);
        FloatBuffer fb = BufferUtils.createFloatBuffer(interleaved.length).put(interleaved); fb.flip();
        glBufferData(GL_ARRAY_BUFFER, fb, GL_STATIC_DRAW);

        long off = 0L;
        glVertexAttribPointer(0,3,GL_FLOAT,false,strideBytes,off); glEnableVertexAttribArray(0); off += 3L*Float.BYTES;
        if (hasNormal){ glVertexAttribPointer(1,3,GL_FLOAT,false,strideBytes,off); glEnableVertexAttribArray(1); off += 3L*Float.BYTES; }
        if (hasUV){     glVertexAttribPointer(2,2,GL_FLOAT,false,strideBytes,off); glEnableVertexAttribArray(2); }

        if (indices!=null && indices.length>0){
            indexed = true; vertexCount = indices.length;
            ebo = glGenBuffers(); glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
            IntBuffer ib = BufferUtils.createIntBuffer(indices.length).put(indices); ib.flip();
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, ib, GL_STATIC_DRAW);
        } else {
            indexed = false; vertexCount = interleaved.length / strideFloats; ebo = 0;
        }

        glBindBuffer(GL_ARRAY_BUFFER,0); glBindVertexArray(0);
    }

    public static Mesh loadOBJ(String filePath, boolean flipV) throws IOException {
        ObjReader.Result r = ObjReader.read(Path.of(filePath), flipV);
        return new Mesh(r.interleaved, r.indices, r.hasNormal, r.hasUV);
    }

    // ★ 新增：从 classpath 资源加载
    public static Mesh loadOBJResource(String resourcePath, boolean flipV) throws IOException {
        ObjReader.Result r = ObjReader.readResource(resourcePath, flipV);
        return new Mesh(r.interleaved, r.indices, r.hasNormal, r.hasUV);
    }

    // ① 便捷版：只有位置属性（location=0），不带索引
    public static Mesh fromInterleaved(float[] positionsOnly) {
        return new Mesh(positionsOnly, null, /*hasNormal*/ false, /*hasUV*/ false);
    }

    // ② 通用版：interleaved = [pos(3) + (opt normal3) + (opt uv2)], 可带索引
    public static Mesh fromInterleaved(float[] interleaved, int[] indices,
                                       boolean hasNormal, boolean hasUV) {
        return new Mesh(interleaved, indices, hasNormal, hasUV);
    }



    public void bind(){ glBindVertexArray(vao); if (ebo!=0) glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo); }
    public void unbind(){ if (ebo!=0) glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0); glBindVertexArray(0); }
    public void draw(){ if (indexed) glDrawElements(GL_TRIANGLES, vertexCount, GL_UNSIGNED_INT, 0L);
    else glDrawArrays(GL_TRIANGLES, 0, vertexCount); }
    public void dispose(){ if (ebo!=0) glDeleteBuffers(ebo); glDeleteBuffers(vbo); glDeleteVertexArrays(vao); }
    @Override public void close(){ dispose(); }
}
