(require-lib "rainbow/swing")

(java-import "java.awt.image.BufferedImage")
(java-import "javax.imageio.ImageIO")

(def new-img (width height)
  (BufferedImage new width height (BufferedImage TYPE_INT_ARGB)))

(def << (arg shift) (* arg (coerce (expt 2 shift) 'int)))

(def encode-color (r g b a) (+ (<< a 24)  (<< r 16) (<< g 8) b))

(def set-pixel (img x y (r g b a)) (img 'setRGB x y (encode-color r g b a)))

(def write-img (img output (o format 'png))
  (ImageIO write img format output))

(def plotter ((w h) (origin-x origin-y) scale bg fg)
  (= scale (/ w scale))
  (let img (new-img w h)
    (with (gc   img!getGraphics
           sc   (fn (d orig) (trunc:* scale (- d orig)))
           seen (table))
      (gc 'setColor bg)
      (gc 'fillRect 0 0 w h)
      (gc 'setColor fg)
      (let plot-point (fn (x y)
                          (withs (k (string x "," y)
                                 already (seen k))
                            (= (seen k) t)
                            (gc 'fillRect x y 1 1)
                            already))
      (make-obj
        (plot  (x y)  (plot-point (sc x origin-x) (sc y origin-y)))
        (write (out)  (write-img img out)))))))




