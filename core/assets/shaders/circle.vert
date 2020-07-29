attribute vec2 a_position;

uniform mat4 u_projTrans;

varying vec2 v_renderPosition;

void main() {
	gl_Position = u_projTrans * vec4(a_position, 0.0, 1.0);
	v_renderPosition = a_position;
}
