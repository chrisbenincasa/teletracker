import {
  Avatar,
  Card,
  createStyles,
  CssBaseline,
  Divider,
  Grid,
  IconButton,
  LinearProgress,
  List,
  ListItem,
  ListItemText,
  Paper,
  Switch,
  Theme,
  Typography,
  WithStyles,
  withStyles,
} from '@material-ui/core';
import BeachAccessIcon from '@material-ui/icons/BeachAccess';
import DeleteIcon from '@material-ui/icons/Delete';
import WorkIcon from '@material-ui/icons/Work';
import Slider from '@material-ui/lab/Slider';
import * as R from 'ramda';
import React, { Component } from 'react';
import { connect } from 'react-redux';
import { ValueType } from 'react-select/lib/types';
import { bindActionCreators } from 'redux';
import { loadNetworks } from '../../actions/metadata';
import {
  addNetworkForUser,
  UserAddNetworkPayload,
  updateUserPreferences,
} from '../../actions/user';
import AutoComplete, {
  AutocompleteOption,
} from '../../components/AutoComplete';
import withUser, { WithUserProps } from '../../components/withUser';
import { AppState } from '../../reducers';
import { layoutStyles } from '../../styles';
import { Network, UserPreferences } from '../../types';
import _ from 'lodash';

const styles = (theme: Theme) =>
  createStyles({
    layout: layoutStyles(theme),
    section: {
      marginBottom: theme.spacing.unit * 2,
    },
    sectionHeader: {
      paddingLeft: theme.spacing.unit / 2,
    },
    drawer: {
      width: 240,
      flexShrink: 0,
    },
    drawerPaper: {
      width: 240,
    },
    drawerHeader: {
      display: 'flex',
      alignItems: 'center',
      padding: '0 8px',
      ...theme.mixins.toolbar,
      justifyContent: 'flex-end',
    },
    list: {
      backgroundColor: theme.palette.background.paper,
    },
    card: {
      display: 'flex',
      height: 50,
    },
    cardDetails: {
      display: 'flex',
      flexDirection: 'column',
      flex: '1',
    },
    cardContent: {
      flex: '1 0 auto',
    },
    cardMedia: {
      width: 50,
      height: 50,
    },
    paper: {
      padding: theme.spacing.unit * 2,
      // textAlign: 'center',
      color: theme.palette.text.secondary,
    },
  });

interface OwnProps {
  isAuthed: boolean;
}

interface StateProps {
  networksLoading: boolean;
  networks?: Network[];
}

interface DispatchProps {
  loadNetworks: () => void;
  addNetworkForUser: (payload?: UserAddNetworkPayload) => void;
  updateUserPreferences: (payload?: UserPreferences) => void;
}

type Props = OwnProps &
  StateProps &
  DispatchProps &
  WithStyles<typeof styles, true> &
  WithUserProps;

type Switches = {
  [key: string]: boolean;
};

type State = {
  single?: any;
  switches: Switches;
  formatSlider: number;
};

class Account extends Component<Props, State> {
  state: State = {
    switches: {},
    formatSlider: 3,
  };

  constructor(props: Props) {
    super(props);

    if (props.userSelf) {
      this.state.formatSlider =
        props.userSelf.userPreferences.presentationTypes.length;
    }
  }

  componentDidMount() {
    this.props.loadNetworks();
  }

  componentDidUpdate(oldProps: Props) {
    if (!oldProps.userSelf && this.props.userSelf) {
      this.setState({
        formatSlider: this.props.userSelf.userPreferences.presentationTypes
          .length,
      });
    }
  }

  handleChange = (value: ValueType<AutocompleteOption<Network>>) => {
    if (!R.isNil(value) && !R.is(Array, value)) {
      console.log(value);

      this.props.addNetworkForUser({
        network: (value as AutocompleteOption<Network>).value,
      });
    }
  };

  handleSwitchChange = (switchName: string) => event => {
    this.setState({
      switches: {
        ...this.state.switches,
        [switchName]: event.target.checked,
      },
    });
  };

  handleSliderChange = (event, value) => {
    this.setState({ formatSlider: value });

    let currentPref = this.props.userSelf!.userPreferences.presentationTypes;

    switch (value) {
      case 1:
        currentPref = ['sd'];
        break;
      case 2:
        currentPref = ['sd', 'hd'];
        break;
      case 3:
        currentPref = ['sd', 'hd', '4k'];
        break;
      default:
        break;
    }

    let newPrefs: UserPreferences = {
      ...this.props.userSelf!.userPreferences,
      presentationTypes: currentPref,
    };

    this.updateUserPreferences(newPrefs);
  };

  updateUserPreferences = _.debounce((userPreferences: UserPreferences) => {
    this.props.updateUserPreferences(userPreferences);
  }, 250);

  renderFormat = () => {
    switch (this.state.formatSlider) {
      case 1:
        return 'Standard Definition (SD) only';
      case 2:
        return 'High Definition (HD) and below';
      default:
        return '4K and below';
    }
  };

  renderSettings() {
    let { classes } = this.props;

    let usersNetworks = R.map(
      R.prop('id'),
      this.props.userSelf!.networkSubscriptions,
    );

    const networkOptions: AutocompleteOption<Network>[] = R.map(
      network => ({
        value: network,
        label: network.name,
      }),
      R.sortBy(
        R.prop('name'),
        R.reject(n => R.contains(n.id, usersNetworks), this.props.networks!),
      ),
    );

    return (
      <div className={classes.layout}>
        <CssBaseline />
        <section className={classes.section}>
          <div className={classes.sectionHeader}>
            <Typography variant="h6" gutterBottom>
              Account
            </Typography>
          </div>
          <Card>
            <List className={classes.list}>
              <ListItem>
                <ListItemText
                  primary="Username"
                  secondary={this.props.userSelf!.name}
                />
              </ListItem>
              <ListItem>
                <Avatar>
                  <WorkIcon />
                </Avatar>
                <ListItemText primary="Email" secondary={'What it is'} />
              </ListItem>
              <ListItem>
                <Avatar>
                  <BeachAccessIcon />
                </Avatar>
                <ListItemText primary="Vacation" secondary="July 20, 2014" />
              </ListItem>
            </List>
          </Card>
        </section>
        <section className={classes.section}>
          <div className={classes.sectionHeader}>
            <Typography variant="h6" gutterBottom>
              Networks
            </Typography>
            <Typography
              variant="body1"
              style={{ marginBottom: 10 }}
              gutterBottom={false}
            >
              Configure which networks you subscribe to. These networks will
              show up first when viewing availability for a given item.
            </Typography>
          </div>
          <Card style={{ overflow: 'visible' }}>
            <div
              style={{
                width: '100%',
                display: 'flex',
                padding: 10,
                alignItems: 'center',
              }}
            >
              <Typography style={{ flex: 1 }} variant="body1">
                Show only selected networks
              </Typography>
              <Switch
                checked={this.state.switches.showOnlyNetworks}
                onChange={this.handleSwitchChange('showOnlyNetworks')}
              />
            </div>
            <Divider />
            <AutoComplete
              options={networkOptions}
              handleChange={this.handleChange}
              placeholder="Add a new network"
              styles={{
                container: {
                  padding: 10,
                },
              }}
            />

            <Grid container style={{ padding: 10 }} spacing={8}>
              {this.props.userSelf!.networkSubscriptions.map(network => (
                <Grid item xs={12} sm={6} md={3} key={network.id}>
                  <Paper square elevation={2} className={classes.paper}>
                    <div style={{ display: 'flex' }}>
                      <div style={{ flex: '0.75 0 auto' }}>
                        <img
                          className={classes.cardMedia}
                          src={`/images/logos/${network.slug}/icon.jpg`}
                        />
                      </div>
                      <Typography style={{ alignSelf: 'center' }}>
                        {network.name}
                      </Typography>
                      <IconButton aria-label="Delete" disableRipple>
                        <DeleteIcon fontSize="small" />
                      </IconButton>
                    </div>
                  </Paper>
                </Grid>
              ))}
            </Grid>
          </Card>
        </section>
        <section className={classes.section}>
          <div className={classes.sectionHeader}>
            <Typography variant="h6" gutterBottom>
              Content Preferences
            </Typography>
          </div>
          <Card>
            <div
              style={{
                width: '100%',
                display: 'flex',
                padding: 10,
                alignItems: 'center',
              }}
            >
              <Typography style={{ flex: 1 }} variant="body1">
                Preferred formats
              </Typography>
              <div
                style={{
                  width: '50%',
                  display: 'flex',
                  alignItems: 'center',
                }}
              >
                <Typography style={{ flex: 1, textAlign: 'center' }}>
                  {this.renderFormat()}
                </Typography>
                <Slider
                  style={{
                    width: '50%',
                    padding: '22px 8px 22px 0', // TODO use theme units
                  }}
                  // classes={{ container: classes.slider }}
                  value={this.state.formatSlider}
                  min={1}
                  max={3}
                  step={1}
                  onChange={this.handleSliderChange}
                />
              </div>
            </div>
          </Card>
        </section>
      </div>
    );
  }

  render() {
    let { networksLoading } = this.props;

    return networksLoading ? (
      <div style={{ flexGrow: 1 }}>
        <LinearProgress />
      </div>
    ) : (
      this.renderSettings()
    );
  }
}

const mapStateToProps = (appState: AppState) => {
  return {
    isAuthed: !R.isNil(R.path(['auth', 'token'], appState)),
    networksLoading: appState.metadata.networksLoading,
    networks: appState.metadata.networks,
  };
};

const mapDispatchToProps = dispatch =>
  bindActionCreators(
    {
      loadNetworks,
      addNetworkForUser,
      updateUserPreferences,
    },
    dispatch,
  );

export default withUser(
  withStyles(styles, { withTheme: true })(
    connect(
      mapStateToProps,
      mapDispatchToProps,
    )(Account),
  ),
);
