attribute vec2 a_position;
attribute vec4 a_color;
attribute vec2 a_texCoordBase;
attribute vec2 a_texCoordCenter;

uniform mat4 u_projTrans;

varying vec4 v_color;
varying vec2 v_texCoordBase;
varying vec2 v_texCoordCenter;

void main() {
	gl_Position = u_projTrans * vec4(a_position, 0.0, 1.0);
	v_color = a_color;
	v_texCoordBase = a_texCoordBase;
	v_texCoordCenter = a_texCoordCenter;
}
