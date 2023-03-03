package haven;

public class Coordf {
    public float x;
    public float y;

    public Coordf(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public Coordf(Coord coord) {
        this.x = coord.x;
        this.y = coord.y;
    }

    public Coordf rotate(double angle) {
        final double cos = Math.cos(angle);
        final double sin = Math.sin(angle);
        return new Coordf((float)(x * cos - y * sin), (float)(y * cos + x * sin));
    }

    public Coordf add(float ax, float ay){
        return new Coordf(this.x+ax, this.y+ay);
    }

    public Coordf add(int ax, int ay){
        return add((float)ax,(float)ay);
    }

    public Coordf add(Coord b) {
        return add(b.x,b.y);
    }

    public Coordf div(Coord d) {
        return(new Coordf(Utils.floordiv(this.x, d.x), Utils.floordiv(this.y, d.y)));
    }

    public Coord toCoord() {
        return(new Coord((int)this.x,(int)this.y));
    }

    public Coord round() {
        return new Coord(Math.round(x), Math.round(y));
    }
}
