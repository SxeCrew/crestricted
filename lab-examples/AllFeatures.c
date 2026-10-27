#include <stdio.h>

int abs(int x) {
    int result;
    if (x < 0) {
        result = -x;
    } else {
        result = x;
    }
    return result;
}

int power(int base, int exponent) {
    int result = 1;
    while (exponent > 0) {
        result = result * base;
        exponent = exponent - 1;
    }
    return result;
}

int main() {
    int a;
    int b;
    scanf("%d", &a);
    scanf("%d", &b);
    int result = power(abs(a) + abs(b), 2);
    printf("%d\n", result);
    printf("%d\n", a < b == b > a);
    return 0;
}
