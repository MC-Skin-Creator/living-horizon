#version 330
#extension GL_ARB_separate_shader_objects : require

// Which distant mobs the depth hides, one pixel per mob, in place of OpenGL's occlusion queries:
// the box is projected on the screen, and a grid of the depth inside it is tested against the
// box's nearest point. Visible as soon as one point of the grid is not in front of the box.

uniform sampler2D InSampler;

layout(std140) uniform LhVisibility {
    // From the world, relative to the camera, to the screen.
    mat4 ViewProjection;
    // How many boxes, 1 when the depth is reversed, 1 when it is 0..1.
    vec4 Info;
    // Each box's lowest then highest corner, relative to the camera.
    vec4 Boxes[960];
};

layout(location = 0) out vec4 fragColor;

const int GRID = 8;

void main() {
    int i = int(gl_FragCoord.x);
    if (i >= int(Info.x)) {
        fragColor = vec4(1.0, 1.0, 0.0, 1.0);
        return;
    }
    bool reversed = Info.y > 0.5;
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
        float d = Info.z > 0.5 ? ndc.z : ndc.z * 0.5 + 0.5;
        nearest = reversed ? max(nearest, d) : min(nearest, d);
    }
    float visible = 1.0;
    if (!behind) {
        lowest = clamp(lowest, 0.0, 1.0);
        highest = clamp(highest, 0.0, 1.0);
        if (highest.x > lowest.x && highest.y > lowest.y) {
            visible = 0.0;
            for (int y = 0; y < GRID && visible == 0.0; y++) {
                for (int x = 0; x < GRID; x++) {
                    vec2 uv = mix(lowest, highest, (vec2(x, y) + 0.5) / float(GRID));
                    float scene = texture(InSampler, uv).r;
                    if (reversed ? scene <= nearest : scene >= nearest) {
                        visible = 1.0;
                        break;
                    }
                }
            }
        }
    }
    fragColor = vec4(float(i & 255) / 255.0, float((i >> 8) & 255) / 255.0, visible, 1.0);
}
