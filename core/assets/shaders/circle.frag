#ifdef GL_ES
	precision mediump float;
#endif

uniform vec2 u_center;
uniform float u_radius;
uniform vec4 u_color;
uniform float u_scale;

varying vec2 v_renderPosition;

// https://thebookofshaders.com/07/
float circle(in vec2 center, in float radius) {
	float dist = distance(center, u_center);
	return smoothstep(radius - 0.75 * u_scale,
	                  radius - 0.5 * u_scale,
	                  dist)
	     - smoothstep(radius + 0.5 * u_scale,
	                  radius + 0.75 * u_scale,
	                  dist)
	;
//	return step(radius - 0.5 * u_scale,
//	            dist)
//	     - step(radius + 0.5 * u_scale,
//	            dist)
//		;
}

void main() {
	float fCircle = circle(v_renderPosition, u_radius);

	if (fCircle < 0.2) {
		discard;
	}

	gl_FragColor = fCircle * u_color;
//	gl_FragColor = vec4(0.5, 0.6, 0.7, 1.0);
}
