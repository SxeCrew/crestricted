#include <stdio.h>

int f(int a)
{
    int b = 10;
    int c = a*b;
    return c;
}

int abs(int x)
{
    int result;
    if (x > 0)
    {
        result = x;
    }
    else
    {
        result = -x;
    }
    return result;
}

int g(int a, int b)
{
    while (b > 0)
    {
        a = a*a;
        b = b-1;
    }
    return a;
}

int main()
{
    int a;
    scanf("%d", &a);
    int b = -7;
    int c = g(f(a)+abs(b), 2);
    printf("%d", c);
    return 0;
}