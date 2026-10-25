#version 330
#extension GL_ARB_separate_shader_objects : require

#include <livinghorizon:far_depth.glsl>

// Which distant mobs the terrain hides, one pixel per mob, in place of OpenGL's occlusion queries:
// the box is projected on the screen, and the depth inside it is read pixel by pixel (at most 32
// points a side) against the box's nearest point. Visible as soon as one point is not clearly in
// front of the box. This depth holds the other figures too, drawn by then: a mob in a herd would
// be hidden by its neighbours while one pixel of it showed, and blink as they moved. Only what
// stands well in front hides it, as only the terrain does with OpenGL's queries.

uniform sampler2D InSampler;

layout(std140) uniform LhVisibility {
    // From the world, relative to the camera, to the screen.
    mat4 ViewProjection;
    // How many boxes, 1 when the depth is reversed, 1 when it is 0..1.
    vec4 Info;
    // The depth texture's width and height in pixels, and the game's projection terms a and b.
    vec4 Screen;
    // Each box's lowest then highest corner, relative to the camera.
    vec4 Boxes[960];
};

layout(location = 0) out vec4 fragColor;

// Points read along each side of a box, at most: one per pixel up to there.
const int MOST = 32;
// How much nearer than a box something must be to hide it: a part of the box's distance, in blocks at least.
const float CLEAR = 0.08;
const float CLEAR_MIN = 2.0;

void main() {
    int i = int(gl_FragCoord.x);
    if (i >= int(Info.x)) {
        fragColor = vec4(1.0, 1.0, 0.0, 1.0);
        return;
    }
    bool reversed = Info.y > 0.5, zeroToOne = Info.z > 0.5;
    vec3 low = Boxes[2 * i].xyz, high = Boxes[2 * i + 1].xyz;
    vec2 lowest = vec2(1.0e9), highest = vec2(-1.0e9);
    float nearest = reversed ? 0.0 : 1.0;
    bool behind = false;
    for (int c = 0; c < 8; c++) {
        vec3 corner = vec3((c & 1) != 0 ? high.x : low.x, (c & 2) != 0 ? high.y : low.y, (c & 4) != 0 ? high.z : low.z);
        vec4 clip = ViewProjection * vec4(corner, 1.0);
        // A corner behind the camera: the box reaches the eye, it is shown.
        if (clip.w <= 0.0) {
            behind = true;
            break;
        }
        vec3 ndc = clip.xyz / clip.w;
        vec2 uv = ndc.xy * 0.5 + 0.5;
        lowest = min(lowest, uv);
        highest = max(highest, uv);
        float d = zeroToOne ? ndc.z : ndc.z * 0.5 + 0.5;
        nearest = reversed ? max(nearest, d) : min(nearest, d);
    }
    float visible = 1.0;
    if (!behind) {
        lowest = clamp(lowest, 0.0, 1.0);
        highest = clamp(highest, 0.0, 1.0);
        if (highest.x > lowest.x && highest.y > lowest.y) {
            // Distances in front of the camera, the box's and the depth's alike, from the same terms.
            float box = lhDistance(nearest, Screen.zw, zeroToOne);
            float hides = box - max(CLEAR_MIN, box * CLEAR);
            ivec2 points = ivec2(clamp(ceil((highest - lowest) * Screen.xy), vec2(1.0), vec2(float(MOST))));
            visible = 0.0;
            for (int y = 0; y < points.y && visible == 0.0; y++) {
                for (int x = 0; x < points.x; x++) {
                    vec2 uv = mix(lowest, highest, (vec2(x, y) + 0.5) / vec2(points));
                    if (!(lhDistance(texture(InSampler, uv).r, Screen.zw, zeroToOne) < hides)) {
                        visible = 1.0;
                        break;
                    }
                }
            }
        }
    }
    fragColor = vec4(float(i & 255) / 255.0, float((i >> 8) & 255) / 255.0, visible, 1.0);
}
