package com.rpsbattle.tournament.scheduler;
import java.util.*;
/** Deterministic maximum matching in a general graph, including odd-cycle contraction. */
final class SwissMatching {
 private final List<List<Integer>> edges;private final int n;private final int[] match,parent,base,queue;private final boolean[] used,flower;
 SwissMatching(List<List<Integer>> edges){this.edges=edges;n=edges.size();match=new int[n];parent=new int[n];base=new int[n];queue=new int[n];used=new boolean[n];flower=new boolean[n];Arrays.fill(match,-1);}
 private int ancestor(int a,int b){var seen=new boolean[n];while(true){a=base[a];seen[a]=true;if(match[a]==-1)break;a=parent[match[a]];}while(!seen[base[b]])b=parent[match[base[b]]];return base[b];}
 private void mark(int v,int root,int child){while(base[v]!=root){flower[base[v]]=flower[base[match[v]]]=true;parent[v]=child;child=match[v];v=parent[match[v]];}}
 private int path(int root){Arrays.fill(used,false);Arrays.fill(parent,-1);for(int i=0;i<n;i++)base[i]=i;int head=0,tail=0;queue[tail++]=root;used[root]=true;
  while(head<tail){int v=queue[head++];for(int u:edges.get(v)){if(base[v]==base[u]||match[v]==u)continue;
   if(u==root||match[u]!=-1&&parent[match[u]]!=-1){int common=ancestor(v,u);Arrays.fill(flower,false);mark(v,common,u);mark(u,common,v);for(int i=0;i<n;i++)if(flower[base[i]]){base[i]=common;if(!used[i]){used[i]=true;queue[tail++]=i;}}}
   else if(parent[u]==-1){parent[u]=v;if(match[u]==-1)return u;u=match[u];used[u]=true;queue[tail++]=u;}
  }}return -1;
 }
 int[] solve(){for(int i=0;i<n;i++)if(match[i]==-1){int v=path(i);while(v!=-1){int p=parent[v],next=p==-1?-1:match[p];match[v]=p;if(p!=-1)match[p]=v;v=next;}}return match.clone();}
}
