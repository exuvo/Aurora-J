#ifdef GL_ES
	precision mediump float;
#endif

uniform float u_scale;

uniform sampler2D u_texture;

varying vec4 v_color;
varying vec2 v_texCoordBase;
varying vec2 v_texCoordCenter;

void main() {
	vec4 texColor;
	
//	texColor = texture2D(u_texture, v_texCoordBase);
//
//	if (texColor.rgb == vec3(1.0, 1.0, 1.0)) {
//		texColor = vec4(v_color.rgb, 1.0);
//
//	} else if (texColor.rgba == vec4(0.0, 0.0, 0.0, 1.0)) {
//		texColor = vec4(1.0, 1.0, 1.0, 1.0);
//
//	} else if (texColor.rgba == vec4(0.0, 1.0, 0.0, 1.0)) {
//		texColor = texture2D(u_texture, v_texCoordCenter);
//
//		if (texColor.rgb == vec3(1.0, 1.0, 1.0)) {
//			texColor = vec4(v_color.rgb, 1.0);
//		}
//	}
	
	vec4 texColorBase = texture2D(u_texture, v_texCoordBase);
	vec4 texColorCenter = texture2D(u_texture, v_texCoordCenter);

	// } else if (texColor.rgba == vec4(0.0, 1.0, 0.0, 1.0)) {
	float green = step(2.0, step(1.0, texColorBase.g) + (1.0 - step(0.01, texColorBase.r + texColorBase.b)));
	texColor = (1.0 - green) * texColorBase + green * texColorCenter;

	// if (texColor.rgb == vec3(1.0, 1.0, 1.0)) {
	float white = step(3.0, texColor.r + texColor.g + texColor.b);
	texColor = (1.0 - white) * texColor + white * vec4(v_color.rgb, 1.0);

	// } else if (texColor.rgba == vec4(0.0, 0.0, 0.0, 1.0)) {
	float black = step(2.0, (1.0 - step(0.01, texColorBase.r + texColorBase.g + texColorBase.b)) + step(1.0, texColorBase.a));
	texColor = (1.0 - black) * texColor + black * vec4(0.5, 0.5, 0.5, 1.0);
	
	gl_FragColor = texColor;
}
