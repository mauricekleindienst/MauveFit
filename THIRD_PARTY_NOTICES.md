# Third-party notices

Both apps, Android and iOS, bundle the same third-party material below. The files live once in
`ios/MauveFit/Resources/`; the iOS build copies them into the app bundle and the Android build
reads the same folder as an assets directory (`app/build.gradle.kts`). The texts are shown in
both apps under the open-source licences in Settings.

## Body and muscle shapes: react-native-body-highlighter

Source: https://github.com/HichamELBSI/react-native-body-highlighter
The SVG paths of the male body (front and back) were converted into `ios/MauveFit/Resources/bodymap.json`
(arcs and quadratic curves rewritten as cubic curves, coordinates rounded to 0.1).

```
MIT License

Copyright (c) 2022 ELABBASSI Hicham

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## Exercise illustrations: Everkinetic

Source: https://github.com/everkinetic/data
License: Creative Commons Attribution-ShareAlike 4.0 International (CC BY-SA 4.0),
https://creativecommons.org/licenses/by-sa/4.0/

Illustrations: Everkinetic, CC BY-SA 4.0. Changes: scaled to at most 640 px and reduced to four
grey levels (`ios/MauveFit/Resources/Everkinetic/`). These adapted images are shared under the same
license, CC BY-SA 4.0. Which drawing belongs to which exercise is listed in
`ios/MauveFit/Resources/everkinetic.json`. Which muscle lights up
which region of the body map is listed in `ios/MauveFit/Resources/muscle_regions.json`.

## Exercise data: exercises-dataset

Source: https://github.com/hasaneyldrm/exercises-dataset
Names, categories, muscles, equipment and instructions (`app/src/main/assets/exercises*.json`,
copied into the app at build time). Only the data is used; the dataset's media are not.

```
MIT License

Copyright (c) 2026 Hasan Emir Yıldırım

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation and data files (the "Software"),
to deal in the Software without restriction, including without limitation the
rights to use, copy, modify, merge, publish, distribute, sublicense, and/or
sell copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
