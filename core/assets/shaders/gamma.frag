#ifdef GL_ES
	precision mediump float;
#endif

uniform float u_gamma;
uniform sampler2D u_texture;

varying vec2 v_texCoord;

// https://www.shadertoy.com/view/lscSzl
// approximate gamma 2.2
vec3 toSRGB(vec3 linearRGB) {
	vec3 a = 12.92 * linearRGB;
	vec3 b = 1.055 * pow(linearRGB, vec3(1.0 / 2.4)) - 0.055;
	vec3 c = step(vec3(0.0031308), linearRGB);
	return mix(a, b, c);
}

// approximate gamma 2.4
vec3 toRec2020(vec3 linearRGB) {
	vec3 a = 4.5 * linearRGB;
	vec3 b = 1.0993 * pow(linearRGB, vec3(0.45)) - 0.0993;
	vec3 c = step(vec3(0.0181), linearRGB);
	return mix(a, b, c);
}

void main() {
	const float eps = 0.001;
	
	vec4 texColor = texture2D(u_texture, v_texCoord);
	
	if (abs(u_gamma - 2.2) < eps) {
		gl_FragColor.rgba = vec4(toSRGB(texColor.rgb), 1.0);
	
	} else if (abs(u_gamma - 2.4) < eps) {
		gl_FragColor.rgba = vec4(toRec2020(texColor.rgb), 1.0);
	
	} else {
		gl_FragColor.rgba = vec4(pow(texColor.rgb, vec3(1.0 / u_gamma)), 1.0);
	}
}
