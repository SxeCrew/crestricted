.bytecode 49.0
.class public Output
.super java/lang/Object

.field private static _scanner Ljava/util/Scanner;

.method static <clinit>()V
  .limit stack 3
  .limit locals 0
  new java/util/Scanner
  dup
  getstatic java/lang/System/in Ljava/io/InputStream;
  invokespecial java/util/Scanner/<init>(Ljava/io/InputStream;)V
  putstatic Output/_scanner Ljava/util/Scanner;
  return
.end method

.method public static f(I)I
  .limit stack 2
  .limit locals 3
  bipush 10
  istore 1
  iload 0
  iload 1
  imul
  istore 2
  iload 2
  ireturn
.end method

.method public static abs(I)I
  .limit stack 2
  .limit locals 2
  iload 0
  iconst_0
  if_icmpgt L2
  iconst_0
  goto L3
L2:
  iconst_1
L3:
  ifeq L0
  iload 0
  istore 1
  goto L1
L0:
  iload 0
  ineg
  istore 1
L1:
  iload 1
  ireturn
.end method

.method public static g(II)I
  .limit stack 2
  .limit locals 2
L0:
  iload 1
  iconst_0
  if_icmpgt L2
  iconst_0
  goto L3
L2:
  iconst_1
L3:
  ifeq L1
  iload 0
  iload 0
  imul
  istore 0
  iload 1
  iconst_1
  isub
  istore 1
  goto L0
L1:
  iload 0
  ireturn
.end method

.method public static main()I
  .limit stack 2
  .limit locals 3
  getstatic Output/_scanner Ljava/util/Scanner;
  invokevirtual java/util/Scanner/nextInt()I
  istore 0
  bipush 7
  ineg
  istore 1
  iload 0
  invokestatic Output/f(I)I
  iload 1
  invokestatic Output/abs(I)I
  iadd
  iconst_2
  invokestatic Output/g(II)I
  istore 2
  getstatic java/lang/System/out Ljava/io/PrintStream;
  iload 2
  invokevirtual java/io/PrintStream/print(I)V
  iconst_0
  ireturn
.end method

.method public static main([Ljava/lang/String;)V
  .limit stack 1
  .limit locals 1
  invokestatic Output/main()I
  pop
  return
.end method
