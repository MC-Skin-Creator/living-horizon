#version 330
#extension GL_ARB_separate_shader_objects : require

// Before Distant Horizons fades the game's picture into its terrain: whatever lies outside
// the game's own chunks - the distant figures, and Distant Horizons' merged depth - reads as
// sky, which the fade leaves alone. Only the game's chunks are faded.

uniform sampler2D InSampler;

layout(std140) uniform LhFadeMask {
    // From the screen back to the world, relative to the camera.
    mat4 InverseViewProjection;
    // The game's chunks, relative to the camera: lowest x, lowest z, highest x, highest z.
    vec4 Chunks;
    // 1 when the depth is 0..1, the empty depth.
    vec4 Layout;
};

layout(location = 0) in vec2 texCoord;

void main() {
    float d = texture(InSampler, texCoord).r;
    if (d == Layout.y) discard;
    vec4 ndc = vec4(texCoord * 2.0 - 1.0, Layout.x > 0.5 ? d : d * 2.0 - 1.0, 1.0);
    vec4 world = InverseViewProjection * ndc;
    vec3 at = world.xyz / world.w;
    if (at.x >= Chunks.x && at.x <= Chunks.z && at.z >= Chunks.y && at.z <= Chunks.w) discard;
    gl_FragDepth = Layout.y;
}
