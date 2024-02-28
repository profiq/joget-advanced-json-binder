# Advanced JSON API Data Binder

An improved version of the [JSON API binder](https://dev.joget.org/community/display/DX8/JSON+API+List+Data+Store) included in Joget DX 8.

#### Description
Easily specify dynamic query parameters in your reqeust. This plugin extends the default functionality of the built-in in JSON API Data Binder with additional settings.

#### Full description
This plugin improves upon the functionality of the built-in JSON API data list binder with the following features:
* **Simple query parameter defintion** - The query parameter grid lets you easily specify which query parameters should be set to what dynamic values.
* **Improved datalist export** - Compatible with more APIs thanks to combining multiple access mechanisms.

You can download a sample application pulling data from the Star Wars API with our plugin [here](#).

## Development
You can build the plugin via Docker by running the following command:
```console
./build.sh
```

### Releasing:
- Update the version in `pom.xml`
- Create new tag/release in GitHub
