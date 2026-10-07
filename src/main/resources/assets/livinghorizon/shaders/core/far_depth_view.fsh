#version 330
#extension GL_ARB_separate_shader_objects : require

// The depth view of the debug screen, drawn through the game's own device: near white to far
// black, the sky in dark blue, in a corner of the picture.

uniform sampler2D InSampler;

layout(std140) uniform LhDepthView {
    // The corner it is drawn in, in screen fractions: x, y, width, height.
    vec4 Area;
    // The game's projection terms a and b, then its near and far planes.
    vec4 Terms;
    // 1 when the depth is reversed, 1 when it is 0..1.
    vec4 Layout;
};

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

void main() {
    vec2 uv = (texCoord - Area.xy) / Area.zw;
    if (uv.x < 0.0 || uv.y < 0.0 || uv.x > 1.0 || uv.y > 1.0) discard;
    float d = texture(InSampler, uv).r;
    if (Layout.x > 0.5 ? d <= 0.0 : d >= 1.0) {
        fragColor = vec4(0.08, 0.12, 0.32, 1.0);
        return;
    }
    float z = Layout.y > 0.5 ? d : d * 2.0 - 1.0;
    float linear = Terms.y / (z + Terms.x);
    float near = Terms.z, far = Terms.w;
    // Logarithmic: a block away and the horizon both readable.
    float shade = 1.0 - log(max(linear, near) / near) / log(far / near);
    fragColor = vec4(vec3(shade), 1.0);
}
