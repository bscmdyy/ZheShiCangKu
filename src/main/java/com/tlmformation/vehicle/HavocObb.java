package com.tlmformation.vehicle;

/**
 * Minimal OBB (oriented bounding box) helper - no Minecraft dependencies.
 * Local space: X = right, Y = up, Z = back (model front is -Z), units = blocks.
 *
 * "快餐式"用法：
 *   HavocObb obb = HavocObb.hull(entity.position(), entity.getYRot());
 *   boolean hit = obb.intersects(aabb);          // 和方块/实体 AABB 粗测
 *   AABB envelope = obb.envelope();               // 给实体 setBoundingBox 用
 *   Vec3 muzzle = obb.localToWorld(localMuzzle);  // 炮口等局部点
 */
public final class HavocObb {
    public final double cx, cy, cz;      // centre
    public final double halfW, halfH, halfL; // half sizes
    public final double yaw;             // radians

    public HavocObb(double cx, double cy, double cz, double halfW, double halfH, double halfL, double yaw) {
        this.cx = cx; this.cy = cy; this.cz = cz;
        this.halfW = halfW; this.halfH = halfH; this.halfL = halfL;
        this.yaw = yaw;
    }

    /** The 8 corners in local space (x=±halfW, y=0..2*halfH, z=±halfL). */
    public double[][] localCorners() {
        double[][] out = new double[8][3];
        int i = 0;
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sy = 0; sy <= 1; sy++) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    out[i][0] = sx * halfW;
                    out[i][1] = sy * 2.0 * halfH;
                    out[i][2] = sz * halfL;
                    i++;
                }
            }
        }
        return out;
    }

    /** Local (x,y,z) -> world, rotating around Y by -yaw (model front -Z). */
    public double[] localToWorld(double x, double y, double z) {
        double c = Math.cos(-yaw), s = Math.sin(-yaw);
        return new double[]{
                cx + x * c + z * s,
                cy + y,
                cz - x * s + z * c
        };
    }

    /** World corners (8). */
    public double[][] worldCorners() {
        double[][] local = localCorners();
        double[][] out = new double[8][3];
        for (int i = 0; i < 8; i++) {
            out[i] = localToWorld(local[i][0], local[i][1], local[i][2]);
        }
        return out;
    }

    /** Axis-aligned envelope (for Entity#setBoundingBox / makeBoundingBox). */
    public net.minecraft.world.phys.AABB envelope() {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (double[] p : worldCorners()) {
            minX = Math.min(minX, p[0]); minY = Math.min(minY, p[1]); minZ = Math.min(minZ, p[2]);
            maxX = Math.max(maxX, p[0]); maxY = Math.max(maxY, p[1]); maxZ = Math.max(maxZ, p[2]);
        }
        return new net.minecraft.world.phys.AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /** Quick AABB-vs-OBB test (SAT on 15 axes). */
    public boolean intersects(net.minecraft.world.phys.AABB box) {
        double bcx = (box.minX + box.maxX) / 2.0;
        double bcy = (box.minY + box.maxY) / 2.0;
        double bcz = (box.minZ + box.maxZ) / 2.0;
        double bhx = (box.maxX - box.minX) / 2.0;
        double bhy = (box.maxY - box.minY) / 2.0;
        double bhz = (box.maxZ - box.minZ) / 2.0;

        double c = Math.cos(-yaw), s = Math.sin(-yaw);
        double[] ax = {c, 0, -s};       // OBB local X in world
        double[] ay = {0, 1, 0};
        double[] az = {s, 0, c};        // OBB local Z in world

        double dx = cx - bcx, dy = cy + halfH - bcy, dz = cz - bcz;

        // AABB axes
        for (int k = 0; k < 3; k++) {
            double[] axis = k == 0 ? new double[]{1, 0, 0} : k == 1 ? new double[]{0, 1, 0} : new double[]{0, 0, 1};
            double ra = bhx * Math.abs(axis[0]) + bhy * Math.abs(axis[1]) + bhz * Math.abs(axis[2]);
            double rb = halfW * Math.abs(dot(ax, axis)) + halfH * Math.abs(dot(ay, axis)) + halfL * Math.abs(dot(az, axis));
            if (Math.abs(dot(new double[]{dx, dy, dz}, axis)) > ra + rb) return false;
        }
        // OBB axes
        double[][] obbAxes = {ax, ay, az};
        for (double[] axis : obbAxes) {
            double ra = bhx * Math.abs(axis[0]) + bhy * Math.abs(axis[1]) + bhz * Math.abs(axis[2]);
            double rb = halfW * Math.abs(dot(ax, axis)) + halfH * Math.abs(dot(ay, axis)) + halfL * Math.abs(dot(az, axis));
            if (Math.abs(dot(new double[]{dx, dy, dz}, axis)) > ra + rb) return false;
        }
        // cross axes (AABB axes x OBB axes) - simplified 9 axes
        double[][] aabbAxes = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
        for (double[] a : aabbAxes) {
            for (double[] b : obbAxes) {
                double[] axis = cross(a, b);
                double len = length(axis);
                if (len < 1.0e-6) continue;
                axis = new double[]{axis[0] / len, axis[1] / len, axis[2] / len};
                double ra = bhx * Math.abs(axis[0]) + bhy * Math.abs(axis[1]) + bhz * Math.abs(axis[2]);
                double rb = halfW * Math.abs(dot(ax, axis)) + halfH * Math.abs(dot(ay, axis)) + halfL * Math.abs(dot(az, axis));
                if (Math.abs(dot(new double[]{dx, dy, dz}, axis)) > ra + rb) return false;
            }
        }
        return true;
    }

    private static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }

    private static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static double length(double[] a) { return Math.sqrt(dot(a, a)); }
}
