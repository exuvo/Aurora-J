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
	
//	if (outside || noinside) {
		texColor = texture2D(u_texture, v_texCoordBase);
	
		if (texColor.rgb == vec3(1.0, 1.0, 1.0)) {
			texColor = vec4(v_color.rgb, 1.0);
			
		} else if (texColor.rgba == vec4(0.0, 0.0, 0.0, 1.0)) {
			texColor = vec4(1.0, 1.0, 1.0, 1.0);
			
		} else if (texColor.rgba == vec4(0.0, 1.0, 0.0, 1.0)) {
			texColor = texture2D(u_texture, v_texCoordCenter);
			
			if (texColor.rgb == vec3(1.0, 1.0, 1.0)) {
				texColor = vec4(v_color.rgb, 1.0);
			}
		}

//	} else {
//		texColor = texture2D(u_texture, v_texCoordCenter);
//	}
	
	gl_FragColor = texColor;
}
