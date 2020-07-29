#ifdef GL_ES
	precision mediump float;
#endif

uniform vec2 u_center;
uniform float u_radius;
uniform vec4 u_color;
//uniform float u_scale;

varying vec2 v_renderPosition;

// https://thebookofshaders.com/07/
float circle(in vec2 center, in float radius){
//	return 1.0 - smoothstep(radius,
//	                        radius + 1 * u_scale,
//													distance(center, u_center));
	return 1.0 - step(radius,
	                  distance(center, u_center));
}

void main(){
	float fCircle = circle(v_renderPosition, u_radius);

	if (fCircle < 0.2) {
		discard;
	}

	gl_FragColor = fCircle * u_color;
//	gl_FragColor = vec4(0.5, 0.6, 0.7, 1.0);
}
