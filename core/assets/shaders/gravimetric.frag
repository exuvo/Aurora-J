#ifdef GL_ES
	precision mediump float;
#endif

//uniform vec2 u_center;
//uniform sampler2D u_texture;

//varying vec4 v_center;
//varying vec2 v_texCoord;
	varying vec4 v_color;

vec4 lerpColors(float lerp, vec4 low, vec4 mid, vec4 high) {
	vec4 a = mix(low, mid, clamp(lerp, -1, 0) + 1);
	vec4 b = mix(mid, high, clamp(lerp, 0, 1));
	return mix(a, b, lerp / 2 + 1);
}

const vec4 lowColor = vec4(0, 0, 1, 1);
const vec4 highColor = vec4(1, 0, 0, 1);
const vec4 middleColor = vec4(0, 0, 0, 0);

void main() {
	gl_FragColor = v_color;

//	vec2 position = gl_FragCoord.xy;
//	vec2 center = u_center.xy;

//	vec4 texColor = texture2D(u_texture, v_texCoord);
//	gl_FragColor = texColor;
//	gl_FragColor = vec4(texColor.r, v_texCoord, 1);
//	gl_FragColor = vec4(clamp(texColor.r, 0, 1), v_texCoord, 1);
//	gl_FragColor = vec4(texColor.rgb, 1);

//	float height = texColor.r;
//	gl_FragColor = vec4(height, 0.1, 0.1, 1);
//	gl_FragColor = lerpColors(height, lowColor, middleColor, highColor);

//	gl_FragColor = lerpColors(v_texCoord.x + v_texCoord.y - 1, lowColor, middleColor, highColor);
}
