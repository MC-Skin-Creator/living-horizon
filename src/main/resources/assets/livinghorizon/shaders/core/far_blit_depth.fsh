#version 330
#extension GL_ARB_separate_shader_objects : require

// One depth texture drawn into another, for the game versions that no longer have a pipeline
// of their own for it (26.4).

uniform sampler2D InSampler;

layout(location = 0) in vec2 texCoord;

void main() {
    gl_FragDepth = texture(InSampler, texCoord).r;
}
