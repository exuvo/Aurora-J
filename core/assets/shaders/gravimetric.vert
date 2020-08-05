attribute vec4 a_color;
attribute vec2 a_position;
//attribute vec2 a_texCoord1;

//uniform vec2 u_center;
uniform mat4 u_projTrans;

//varying vec4 v_center;
//varying vec2 v_texCoord;
varying vec4 v_color;

void main() {
//	v_center = u_projTrans * vec4(u_center, 0.0, 1.0);
//	v_texCoord = a_texCoord1;
	gl_Position = u_projTrans * vec4(a_position, 0.0, 1.0);
	v_color = a_color;
}
