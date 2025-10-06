import org.lwjgl.glfw.GLFWScrollCallbackI;
import org.lwjgl.system.MemoryStack;

import static java.lang.Math.*;
import static org.lwjgl.glfw.GLFW.*;

public class Camera {
    private final long window;

    private final float[] target = new float[]{0f, 0f, 0f};
    private float distance = 3.0f;
    private float minDistance = 0.05f;
    private float maxDistance = 1_000_000f;

    private float fovDeg = 60f;
    private float near = 0.02f;
    private float far = 10_000_000f;

    private boolean rotating = false;
    private boolean panning = false;

    private double lastX, lastY;
    private final float[] arcballV0 = new float[3];
    private final float[] arcballV1 = new float[3];

    private final float[] rotation = new float[]{1, 0, 0, 0};

    private float panSpeed = 1.0f;
    private float zoomSpeed = 0.12f;
    private float keyPanSpeed = 2.0f;
    private float keyZoomSpeed = 2.0f;
    private float keyRotSpeedDeg = 90f;
    private float shiftMultiplier = 3.0f;

    private double lastTime;

    public Camera(long window) {
        this.window = window;
        installCallbacks();
        lastTime = glfwGetTime();
    }

    public void setTarget(float x, float y, float z) {
        target[0] = x;
        target[1] = y;
        target[2] = z;
    }

    public void setDistance(float d) {
        distance = clamp(d, minDistance, maxDistance);
        ensureFarForDistance();
    }

    public void setLimits(float minD, float maxD) {
        this.minDistance = minD;
        this.maxDistance = maxD;
    }

    public void setPanSpeed(float s) {
        this.panSpeed = s;
    }

    public void setKeySpeeds(float panUnitsPerSec, float zoomPerSec, float rotDegPerSec) {
        this.keyPanSpeed = panUnitsPerSec;
        this.keyZoomSpeed = zoomPerSec;
        this.keyRotSpeedDeg = rotDegPerSec;
    }

    public void setShiftMultiplier(float multiplier) {
        this.shiftMultiplier = multiplier;
    }

    public void setProjection(float fovDeg, float near, float far) {
        this.fovDeg = fovDeg;
        this.near = near;
        this.far = far;
        ensureFarForDistance();
    }

    private void ensureFarForDistance() {
        float safety = 2.0f;
        if (distance * safety > far) {
            far = min(distance * safety, 1_000_000_000f);
        }
        if (distance > maxDistance * 0.9f) {
            maxDistance = min(maxDistance * 2.0f, 1_000_000_000f);
        }
    }

    public void fitRadius(float radius) {
        int width;
        int height;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var w = stack.mallocInt(1);
            var h = stack.mallocInt(1);
            glfwGetFramebufferSize(window, w, h);
            width = max(1, w.get(0));
            height = max(1, h.get(0));
        }
        float aspect = (float) width / (float) height;
        float fovy = (float) toRadians(fovDeg);
        float fovx = 2f * (float) atan(tan(fovy / 2f) * aspect);
        float dzY = radius / (float) tan(fovy / 2f);
        float dzX = radius / (float) tan(fovx / 2f);
        float dist = max(dzX, dzY);
        setDistance(dist * 1.2f);
    }

    public void frame(float cx, float cy, float cz, float radius) {
        setTarget(cx, cy, cz);
        fitRadius(max(1e-6f, radius));
    }

    public void update() {
        double now = glfwGetTime();
        float dt = (float) (now - lastTime);
        lastTime = now;
        if (dt <= 0f) {
            return;
        }

        boolean boosting = glfwGetKey(window, GLFW_KEY_LEFT_SHIFT) == GLFW_PRESS
                || glfwGetKey(window, GLFW_KEY_RIGHT_SHIFT) == GLFW_PRESS;
        float boost = boosting ? shiftMultiplier : 1.0f;

        float[] right = rotateVec3(rotation, new float[]{1, 0, 0});
        float[] upVec = rotateVec3(rotation, new float[]{0, 1, 0});
        normalize3(right);
        normalize3(upVec);

        int upKey = (glfwGetKey(window, GLFW_KEY_W) == GLFW_PRESS ? 1 : 0)
                - (glfwGetKey(window, GLFW_KEY_S) == GLFW_PRESS ? 1 : 0);
        int rightKey = (glfwGetKey(window, GLFW_KEY_D) == GLFW_PRESS ? 1 : 0)
                - (glfwGetKey(window, GLFW_KEY_A) == GLFW_PRESS ? 1 : 0);
        if (upKey != 0 || rightKey != 0) {
            float base = distance * keyPanSpeed * dt * boost;
            target[0] += (-rightKey) * base * right[0] + (upKey) * base * upVec[0];
            target[1] += (-rightKey) * base * right[1] + (upKey) * base * upVec[1];
            target[2] += (-rightKey) * base * right[2] + (upKey) * base * upVec[2];
        }

        int zoomKey = (glfwGetKey(window, GLFW_KEY_Q) == GLFW_PRESS ? 1 : 0)
                - (glfwGetKey(window, GLFW_KEY_E) == GLFW_PRESS ? 1 : 0);
        if (zoomKey != 0) {
            float factor = (float) exp(-zoomKey * keyZoomSpeed * dt * boost);
            setDistance(distance * factor);
        }

        float rotRad = (float) toRadians(keyRotSpeedDeg) * dt * boost;

        int yawKey = (glfwGetKey(window, GLFW_KEY_LEFT) == GLFW_PRESS ? 1 : 0)
                - (glfwGetKey(window, GLFW_KEY_RIGHT) == GLFW_PRESS ? 1 : 0);
        int pitchKey = (glfwGetKey(window, GLFW_KEY_UP) == GLFW_PRESS ? 1 : 0)
                - (glfwGetKey(window, GLFW_KEY_DOWN) == GLFW_PRESS ? 1 : 0);
        int rollKey = (glfwGetKey(window, GLFW_KEY_Z) == GLFW_PRESS ? 1 : 0)
                - (glfwGetKey(window, GLFW_KEY_X) == GLFW_PRESS ? 1 : 0);

        if (yawKey != 0) {
            float[] axisY = new float[]{0, 1, 0};
            applyAxisAngle(axisY, yawKey * rotRad);
        }
        if (pitchKey != 0) {
            float[] axisR = rotateVec3(rotation, new float[]{1, 0, 0});
            normalize3(axisR);
            applyAxisAngle(axisR, pitchKey * rotRad);
        }
        if (rollKey != 0) {
            float[] axisF = rotateVec3(rotation, new float[]{0, 0, 1});
            normalize3(axisF);
            applyAxisAngle(axisF, rollKey * rotRad);
        }

        if (glfwGetKey(window, GLFW_KEY_R) == GLFW_PRESS) {
            rotation[0] = 1f;
            rotation[1] = rotation[2] = rotation[3] = 0f;
        }
    }

    public float[] getViewProjection() {
        int width;
        int height;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var w = stack.mallocInt(1);
            var h = stack.mallocInt(1);
            glfwGetFramebufferSize(window, w, h);
            width = max(1, w.get(0));
            height = max(1, h.get(0));
        }
        float aspect = (float) width / (float) height;

        float[] eye = rotateVec3(rotation, new float[]{0, 0, distance});
        eye[0] += target[0];
        eye[1] += target[1];
        eye[2] += target[2];

        float[] up = rotateVec3(rotation, new float[]{0, 1, 0});

        float[] view = lookAt(eye[0], eye[1], eye[2], target[0], target[1], target[2], up[0], up[1], up[2]);
        float[] proj = perspective(fovDeg, aspect, near, far);
        return multiply(proj, view);
    }

    public static float[] identityMatrix() {
        float[] m = new float[16];
        m[0] = 1f;
        m[5] = 1f;
        m[10] = 1f;
        m[15] = 1f;
        return m;
    }

    public static float[] multiply(float[] a, float[] b) {
        float[] r = new float[16];
        for (int c = 0; c < 4; c++) {
            for (int r0 = 0; r0 < 4; r0++) {
                r[c * 4 + r0] = a[0 * 4 + r0] * b[c * 4 + 0]
                        + a[1 * 4 + r0] * b[c * 4 + 1]
                        + a[2 * 4 + r0] * b[c * 4 + 2]
                        + a[3 * 4 + r0] * b[c * 4 + 3];
            }
        }
        return r;
    }

    private void installCallbacks() {
        glfwSetMouseButtonCallback(window, (w, button, action, mods) -> {
            double x = getCursorX();
            double y = getCursorY();
            lastX = x;
            lastY = y;

            if (button == GLFW_MOUSE_BUTTON_LEFT) {
                rotating = (action == GLFW_PRESS);
                if (rotating) {
                    mapToSphere(x, y, arcballV0);
                }
            } else if (button == GLFW_MOUSE_BUTTON_MIDDLE || button == GLFW_MOUSE_BUTTON_RIGHT) {
                panning = (action == GLFW_PRESS);
            }
        });

        glfwSetCursorPosCallback(window, (w, x, y) -> {
            if (rotating) {
                mapToSphere(x, y, arcballV1);
                applyArcballDrag(arcballV0, arcballV1);
                System.arraycopy(arcballV1, 0, arcballV0, 0, 3);
            } else if (panning) {
                panByPixelDelta(x - lastX, y - lastY);
            }
            lastX = x;
            lastY = y;
        });

        glfwSetScrollCallback(window, (GLFWScrollCallbackI) (w, xoff, yoff) -> {
            float factor = (float) exp(-yoff * zoomSpeed);
            setDistance(distance * factor);
        });
    }

    private void mapToSphere(double mx, double my, float[] out) {
        int width;
        int height;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var w = stack.mallocInt(1);
            var h = stack.mallocInt(1);
            glfwGetFramebufferSize(window, w, h);
            width = max(1, w.get(0));
            height = max(1, h.get(0));
        }
        double s = min(width, height);
        double x = (2.0 * mx - width) / s;
        double y = (height - 2.0 * my) / s;
        double r2 = x * x + y * y;

        if (r2 <= 1.0) {
            out[0] = (float) x;
            out[1] = (float) y;
            out[2] = (float) sqrt(1.0 - r2);
        } else {
            double inv = 1.0 / sqrt(r2);
            out[0] = (float) (x * inv);
            out[1] = (float) (y * inv);
            out[2] = 0f;
        }
    }

    private void applyArcballDrag(float[] v0, float[] v1) {
        float dot = clamp(dot(v0, v1), -1f, 1f);
        float angle = (float) acos(dot);
        float[] axis = cross(v0, v1);
        float len = length(axis);
        if (len < 1e-6f || angle == 0f) {
            return;
        }
        axis[0] /= len;
        axis[1] /= len;
        axis[2] /= len;
        applyAxisAngle(axis, angle);
    }

    private void applyAxisAngle(float[] axis, float angle) {
        float half = angle * 0.5f;
        float s = (float) sin(half);
        float[] dq = new float[]{(float) cos(half), axis[0] * s, axis[1] * s, axis[2] * s};
        multiplyQuat(dq, rotation, rotation);
        normalizeQuat(rotation);
    }

    private void panByPixelDelta(double dx, double dy) {
        int w;
        int h;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var wi = stack.mallocInt(1);
            var hi = stack.mallocInt(1);
            glfwGetFramebufferSize(window, wi, hi);
            w = max(1, wi.get(0));
            h = max(1, hi.get(0));
        }
        float scale = (float) (distance * panSpeed / min(w, h));
        float[] right = rotateVec3(rotation, new float[]{1, 0, 0});
        float[] upVec = rotateVec3(rotation, new float[]{0, 1, 0});
        target[0] -= (float) dx * scale * right[0] - (float) dy * scale * upVec[0];
        target[1] -= (float) dx * scale * right[1] - (float) dy * scale * upVec[1];
        target[2] -= (float) dx * scale * right[2] - (float) dy * scale * upVec[2];
    }

    private static void normalize3(float[] v) {
        float l = (float) sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        if (l > 0f) {
            v[0] /= l;
            v[1] /= l;
            v[2] /= l;
        }
    }

    private static float dot(float[] a, float[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static float length(float[] v) {
        return (float) sqrt(dot(v, v));
    }

    private static float[] cross(float[] a, float[] b) {
        return new float[]{
                a[1] * b[2] - a[2] * b[1],
                a[2] * b[0] - a[0] * b[2],
                a[0] * b[1] - a[1] * b[0]
        };
    }

    private static void multiplyQuat(float[] q1, float[] q2, float[] out) {
        float w1 = q1[0], x1 = q1[1], y1 = q1[2], z1 = q1[3];
        float w2 = q2[0], x2 = q2[1], y2 = q2[2], z2 = q2[3];
        out[0] = w1 * w2 - x1 * x2 - y1 * y2 - z1 * z2;
        out[1] = w1 * x2 + x1 * w2 + y1 * z2 - z1 * y2;
        out[2] = w1 * y2 - x1 * z2 + y1 * w2 + z1 * x2;
        out[3] = w1 * z2 + x1 * y2 - y1 * x2 + z1 * w2;
    }

    private static void normalizeQuat(float[] q) {
        float l = (float) sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3]);
        if (l == 0f) {
            q[0] = 1f;
            q[1] = q[2] = q[3] = 0f;
            return;
        }
        q[0] /= l;
        q[1] /= l;
        q[2] /= l;
        q[3] /= l;
    }

    private static float[] rotateVec3(float[] q, float[] v) {
        float w = q[0], x = q[1], y = q[2], z = q[3];
        float tx = 2f * (y * v[2] - z * v[1]);
        float ty = 2f * (z * v[0] - x * v[2]);
        float tz = 2f * (x * v[1] - y * v[0]);
        return new float[]{
                v[0] + w * tx + (y * tz - z * ty),
                v[1] + w * ty + (z * tx - x * tz),
                v[2] + w * tz + (x * ty - y * tx)
        };
    }

    private static float[] perspective(float fovDeg, float aspect, float near, float far) {
        float f = 1f / (float) tan(toRadians(fovDeg) / 2.0);
        float[] m = new float[16];
        m[0] = f / aspect;
        m[5] = f;
        m[10] = (far + near) / (near - far);
        m[11] = -1f;
        m[14] = (2f * far * near) / (near - far);
        return m;
    }

    private static float[] lookAt(float ex, float ey, float ez,
                                  float cx, float cy, float cz,
                                  float ux, float uy, float uz) {
        float fx = cx - ex;
        float fy = cy - ey;
        float fz = cz - ez;
        float fl = (float) sqrt(fx * fx + fy * fy + fz * fz);
        fx /= fl;
        fy /= fl;
        fz /= fl;

        float sx = fy * uz - fz * uy;
        float sy = fz * ux - fx * uz;
        float sz = fx * uy - fy * ux;
        float sl = (float) sqrt(sx * sx + sy * sy + sz * sz);
        sx /= sl;
        sy /= sl;
        sz /= sl;

        float ux2 = sy * fz - sz * fy;
        float uy2 = sz * fx - sx * fz;
        float uz2 = sx * fy - sy * fx;

        float[] m = new float[16];
        m[0] = sx;
        m[4] = ux2;
        m[8] = -fx;
        m[12] = 0f;

        m[1] = sy;
        m[5] = uy2;
        m[9] = -fy;
        m[13] = 0f;

        m[2] = sz;
        m[6] = uz2;
        m[10] = -fz;
        m[14] = 0f;

        m[3] = 0f;
        m[7] = 0f;
        m[11] = 0f;
        m[15] = 1f;

        return multiply(m, translate(-ex, -ey, -ez));
    }

    private static float[] translate(float x, float y, float z) {
        float[] m = identityMatrix();
        m[12] = x;
        m[13] = y;
        m[14] = z;
        return m;
    }

    private double getCursorX() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var x = stack.mallocDouble(1);
            var y = stack.mallocDouble(1);
            glfwGetCursorPos(window, x, y);
            return x.get(0);
        }
    }

    private double getCursorY() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var x = stack.mallocDouble(1);
            var y = stack.mallocDouble(1);
            glfwGetCursorPos(window, x, y);
            return y.get(0);
        }
    }
}
