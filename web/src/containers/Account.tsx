import React, { Component } from 'react';
import {
  Card,
  createStyles,
  Divider,
  Grid,
  Icon,
  LinearProgress,
  List,
  ListItem,
  ListItemSecondaryAction,
  ListItemText,
  Paper,
  Switch,
  TextField,
  Theme,
  Tooltip,
  Typography,
  WithStyles,
  withStyles,
} from '@material-ui/core';
import AddIcon from '@material-ui/icons/Add';
import CheckIcon from '@material-ui/icons/Check';
import Slider from '@material-ui/core/Slider';
import _ from 'lodash';
import * as R from 'ramda';
import { connect } from 'react-redux';
import { ValueType } from 'react-select/lib/types';
import { bindActionCreators } from 'redux';
import { loadNetworks } from '../actions/metadata';
import {
  updateNetworksForUser,
  updateUserPreferences,
  UserUpdateNetworksPayload,
} from '../actions/user';
import withUser, { WithUserProps } from '../components/withUser';
import { AppState } from '../reducers';
import { Network, UserPreferences } from '../types';

const styles = (theme: Theme) =>
  createStyles({
    section: {
      marginBottom: theme.spacing(2),
      display: 'flex',
      flexDirection: 'column',
    },
    sectionHeader: {
      paddingLeft: theme.spacing(0.5),
    },
    list: {
      backgroundColor: theme.palette.background.paper,
    },
    cardMedia: {
      width: 45,
      height: 45,
      margin: theme.spacing(0.25),
      borderRadius: theme.shape.borderRadius,
    },
    paper: {
      color: theme.palette.text.secondary,
      '&:hover': {
        backgroundColor: theme.palette.grey[100],
        cursor: 'pointer',
      },
    },
    paperSelected: {
      backgroundColor: theme.palette.primary.main,
      color: theme.palette.getContrastText(theme.palette.primary.main),
      '&:hover': {
        backgroundColor: theme.palette.primary.light,
        cursor: 'pointer',
      },
    },
    filterSearchField: {
      padding: theme.spacing(0.5, 0),
      margin: theme.spacing(0, 0, 0.6),
    },
  });

interface OwnProps {
  readonly isAuthed: boolean;
  readonly drawerOpen: boolean;
}

interface StateProps {
  readonly networksLoading: boolean;
  readonly networks?: ReadonlyArray<Network>;
}

interface DispatchProps {
  readonly loadNetworks: () => void;
  readonly updateNetworksForUser: (payload?: UserUpdateNetworksPayload) => void;
  readonly updateUserPreferences: (payload?: UserPreferences) => void;
}

type Props = OwnProps &
  StateProps &
  DispatchProps &
  WithStyles<typeof styles, true> &
  WithUserProps;

type SwitchNames = 'showOnlyNetworks';

type Switches = { [key in SwitchNames]: boolean };

type State = {
  switches: Switches;
  formatSlider: number;
  networkFilter: string;
};

class Account extends Component<Props, State> {
  state: State = {
    switches: {
      showOnlyNetworks: false,
    },
    formatSlider: 3,
    networkFilter: '',
  };

  constructor(props: Props) {
    super(props);

    if (props.userSelf) {
      this.state.formatSlider = (
        props.userSelf?.preferences?.presentationTypes || []
      ).length;

      this.state.switches.showOnlyNetworks =
        props.userSelf?.preferences?.showOnlyNetworkSubscriptions || false;
    }
  }

  componentDidMount() {
    this.props.loadNetworks();
  }

  componentDidUpdate(oldProps: Props) {
    if (!oldProps.userSelf && this.props.userSelf) {
      this.setState({
        formatSlider: (
          this.props.userSelf?.preferences?.presentationTypes || []
        ).length,
        switches: {
          ...this.state.switches,
          showOnlyNetworks:
            this.props.userSelf?.preferences?.showOnlyNetworkSubscriptions ||
            false,
        },
      });
    }
  }

  handleClickItem = (network: Network) => {
    if (this.isSubscribedToNetwork(network)) {
      this.props.updateNetworksForUser({
        add: [],
        remove: [network],
      });
    } else {
      this.props.updateNetworksForUser({
        add: [network],
        remove: [],
      });
    }
  };

  handleSwitchChange = (switchName: string) => event => {
    let newPrefs: UserPreferences = {
      ...this.props.userSelf!.preferences!,
      showOnlyNetworkSubscriptions: event.target.checked,
    };

    this.setState(
      {
        switches: {
          ...this.state.switches,
          [switchName]: event.target.checked,
        },
      },
      () => {
        this.updateUserPreferences(newPrefs);
      },
    );
  };

  handleSliderChange = (event, value) => {
    this.setState({ formatSlider: value });

    let currentPref = this.props.userSelf!.preferences?.presentationTypes || [];

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
      ...this.props.userSelf!.preferences!,
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

  isSubscribedToNetwork = (network: Network) => {
    return R.any(
      R.propEq('slug', network.slug),
      this.props.userSelf!.networks || [],
    );
  };

  renderNetworkGridItem = (network: Network) => {
    let { classes } = this.props;
    let isSubscribed = this.isSubscribedToNetwork(network);

    return (
      <Grid
        item
        xs={12}
        sm={6}
        md={3}
        key={network.id}
        onClick={() => this.handleClickItem(network)}
      >
        <Paper
          elevation={2}
          className={isSubscribed ? classes.paperSelected : classes.paper}
        >
          <div style={{ display: 'flex' }}>
            <div style={{ flex: '0.25 0 auto' }}>
              <img
                className={classes.cardMedia}
                src={`/images/logos/${network.slug}/icon.jpg`}
                alt={`${network.name} logo`}
              />
            </div>
            <Typography
              style={{ alignSelf: 'center', flex: '1 0 auto' }}
              color="inherit"
            >
              {network.name}
            </Typography>
            <div
              style={{
                display: 'flex',
                width: 50,
                height: 50,
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              {isSubscribed ? (
                <CheckIcon fontSize="small" color="inherit" />
              ) : (
                <AddIcon fontSize="small" color="inherit" />
              )}
            </div>
          </div>
        </Paper>
      </Grid>
    );
  };

  renderSettings() {
    let { classes, theme } = this.props;
    let networksToRender: ReadonlyArray<Network>;

    if (this.state.networkFilter && this.state.networkFilter.length > 0) {
      let filter = this.state.networkFilter!.toLowerCase();
      networksToRender = R.filter(
        (n: Network) => R.startsWith(filter, n.name.toLowerCase()),
        this.props.networks!,
      );
    } else {
      networksToRender = [...this.props.networks!];
    }

    return (
      <div style={{ display: 'flex' }}>
        <div style={{ display: 'flex', flexDirection: 'column' }}>
          <section className={classes.section}>
            <div className={classes.sectionHeader}>
              <Typography variant="h6" gutterBottom>
                Account
              </Typography>
            </div>
            <Card>
              <List disablePadding className={classes.list}>
                {/* <ListItem divider>
                  <ListItemText
                    primary="Username"
                    secondary={this.props.userSelf!.username}
                  />
                  <ListItemSecondaryAction>
                    <Icon style={{ verticalAlign: 'middle' }}>arrow_right</Icon>
                  </ListItemSecondaryAction>
                </ListItem> */}
                <ListItem divider>
                  <ListItemText primary="Email" />
                  <ListItemSecondaryAction>
                    <Icon style={{ verticalAlign: 'middle' }}>arrow_right</Icon>
                  </ListItemSecondaryAction>
                </ListItem>
                <ListItem>
                  <ListItemText primary="Password" />
                  <ListItemSecondaryAction>
                    <Icon style={{ verticalAlign: 'middle' }}>arrow_right</Icon>
                  </ListItemSecondaryAction>
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
            <Card style={{ padding: '0 16px' }}>
              <div
                style={{
                  width: '100%',
                  display: 'flex',
                  flexWrap: 'wrap',
                  alignItems: 'center',
                }}
              >
                <Typography style={{ flex: 1 }} variant="body1">
                  Show only selected networks&nbsp;
                  <Tooltip
                    title="When on, item pages will only show availability for your selected networks."
                    placement="top"
                  >
                    <span
                      style={{
                        fontSize: '0.75em',
                        position: 'relative',
                        bottom: 6,
                        color: theme.palette.primary.main,
                      }}
                    >
                      [?]
                    </span>
                  </Tooltip>
                </Typography>
                <Switch
                  color="primary"
                  checked={this.state.switches['showOnlyNetworks']}
                  onChange={this.handleSwitchChange('showOnlyNetworks')}
                />
              </div>
              <Divider />
              <TextField
                fullWidth
                placeholder="Filter networks"
                className={classes.filterSearchField}
                value={this.state.networkFilter}
                onChange={ev =>
                  this.setState({ networkFilter: ev.target.value })
                }
              />
              <Grid container style={{ paddingBottom: 16 }} spacing={1}>
                {networksToRender.map(this.renderNetworkGridItem)}
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
      </div>
    );
  }

  render() {
    let { networks, networksLoading, userSelf } = this.props;

    return (networksLoading || !networks) && !userSelf ? (
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
      updateNetworksForUser,
      updateUserPreferences,
    },
    dispatch,
  );

export default withUser(
  withStyles(styles, { withTheme: true })(
    connect(mapStateToProps, mapDispatchToProps)(Account),
  ),
);
