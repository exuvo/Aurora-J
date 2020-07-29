#ifdef GL_ES
	precision mediump float;
#endif

uniform vec2 u_center;
uniform float u_radius;

varying vec2 v_renderPosition;

float disk(in vec2 center, in float radius){
	return 1.0 - step(radius, distance(center, u_center));
}

void main(){
	float val = disk(v_renderPosition, u_radius);

	if (val < 0.1) {
		discard;
	}

	gl_FragColor = vec4(0.0, 0.0, 0.0, 0.0);
}
