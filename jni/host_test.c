#include <stdio.h>
#include <math.h>
#include "src/ons_dsp.c"
int main(void){
    float maxerr=0; 
    for(int i=-20000;i<20000;i++){ float x=i*0.000314f; float a=ons_sin(x), b=sinf(x); float e=fabsf(a-b); if(e>maxerr) maxerr=e; }
    printf("sin_max_abs_err=%.6f\n", maxerr);
    /* 合成一段，看峰值与是否溢出 */
    static short buf[44100*2];
    int n=44100*19/10; if(n>44100*2)n=44100*2;
    /* 手动内联合成（与 chime 同构，仅本机验证） */
    float f0=528.f; const float inv=1.f/44100.f; int delay=(int)(0.105f*44100); int atk=(int)(0.006f*44100); int fade=(int)(0.08f*44100);
    float e1=1,e2=1,e3=1,e4=1, m1=1-3.1f*inv,m2=1-5.2f*inv,m3=1-7.8f*inv,m4=1-2.2f*inv;
    int peak=0; double rms=0;
    for(int i=0;i<n;i++){ float ph=6.28318530717959f*f0*(float)i*inv;
      float v=1.00f*ons_sin(ph)*e1+0.40f*ons_sin(ph*2)*e2+0.18f*ons_sin(ph*3.01f)*e3+0.08f*ons_sin(ph*0.5f)*e4;
      if(i>=delay) v += ((float)buf[i-delay]/32767.f)*0.28f;
      if(i<atk) v*= (float)i/(float)atk;
      int back=n-1-i; if(back<fade) v*=(float)back/(float)fade;
      if(v>1)v=1; if(v<-1)v=-1; buf[i]=(short)(v*0.32f*32767.f);
      int a=buf[i]<0?-buf[i]:buf[i]; if(a>peak)peak=a; rms+=(double)buf[i]*buf[i];
      e1*=m1;e2*=m2;e3*=m3;e4*=m4; }
    printf("frames=%d peak=%d (%.1f%% FS) rms=%.0f tail=%d\n", n, peak, peak*100.0/32767.0, sqrt(rms/n), buf[n-1]);
    /* 平滑内核验证 */
    float a[7]={0.2f,0.9f,0.1f,1.0f,0.3f,0.5f,0.7f};
    float prev=a[0];
    for(int i=1;i<6;i++){float cur=a[i],nx=a[i+1];a[i]=0.25f*prev+0.5f*cur+0.25f*nx;prev=cur;}
    printf("soften:"); for(int i=0;i<7;i++)printf(" %.3f",a[i]); printf("\n");
    return 0;
}
