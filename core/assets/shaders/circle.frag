#ifdef GL_ES
	precision mediump float;
#endif

uniform vec2 u_center;
uniform float u_radius;
uniform vec3 u_color;
uniform float u_scale;

varying vec2 v_renderPosition;

// https://thebookofshaders.com/07/
float circle(in vec2 center, in float radius) {
	float dist = distance(center, u_center);
	return smoothstep(radius - 1 * u_scale,
	                  radius - 0.5 * u_scale,
	                  dist)
	     - smoothstep(radius + 0.5 * u_scale,
	                  radius + 1 * u_scale,
	                  dist)
	;
}

void main() {
	float val = circle(v_renderPosition, u_radius);

	if (val < 0.1) {
		discard;
	}

	gl_FragColor = vec4(0.85 * u_color.rgb, val);
}
