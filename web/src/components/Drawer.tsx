import {
  Drawer,
} from '@material-ui/core';
import { Link as RouterLink, withRouter } from 'react-router-dom';
import React, { Component, ReactNode } from 'react';
import Truncate from 'react-truncate';
import AddToListDialog from './AddToListDialog';
import { User, List } from '../types';
import { Thing } from "../types";
import { getDescription, getPosterPath } from '../utils/metadata-access';
import { Dispatch, bindActionCreators } from 'redux';
import { ListUpdate, ListUpdatedInitiatedPayload } from '../actions/lists';
import { connect } from 'react-redux';

const styles = (theme: Theme) =>
  createStyles({
    drawer: {
      width: 240,
      flexShrink: 0,
    },
    drawerPaper: {
      width: 240,
    },
  });

/* This is just an array of colors I grabbed from a famous picasso painting.  Testing how the lists could look with a random color identifier. */
const colorArray = [
  '#90bab0',
  '#5b3648',
  '#2b5875',
  '#ab6c5d',
  '#b16f7b',
  '#764b45',
  '#53536d',
  '#7adbd0',
  '#32cad7',
  '#17bfe3',
  '#c2e9e6',
  '#978fb6',
  '#04256a',
  '#3eb1b6',
  '#7266b8',
  '#1172d0',
  '#ed0000',
  '#abae77',
  '#73b06d',
  '#d7799b',
  '#5b7e7a',
  '#6fc16d',
  '#8c8c58',
  '#d8070d',
  '#ca8866',
  '#d9e4e0',
  '#c17b9f',
  '#7eb691',
  '#71dee1',
  '#50bc45',
  '#904317',
  '#292234',
  '#a64e38',
  '#c5c3d1',
  '#825e6a',
  '#234282',
  '#30705f',
  '#be2d00',
  '#8cac23',
  '#9b708b',
  '#6c703d',
  '#c09f12',
  '#265e97',
  '#d21b39',
  '#948c5b',
  '#6d6536',
  '#778588',
  '#c2350a',
  '#5ea6b4',
];

interface OwnProps {
  listsById: ListsByIdMap;
  loadingLists: boolean;
}

interface DispatchProps {
  ListRetrieveAllInitiated: (payload?: ListRetrieveAllPayload) => void;
}


interface State {
  createDialogOpen: boolean;
}

type Props = OwnProps &
  DispatchProps &
  WithStyles<typeof styles> &
  WithUserProps;


class Drawer extends Component<Props, ItemCardState> {
  static defaultProps = {
    withActionButton: false,
    itemCardVisible: true,
  };

  state: State = {
    createDialogOpen: false,
  };

  constructor(props: Props) {
    super(props);
    if (
      !props.listContext &&
      props.withActionButton &&
      process.env.NODE_ENV !== 'production'
    ) {
      console.warn('withActionButton=true without listContext will not work.');
    }
  }

  handleModalOpen = (item: Thing) => {
    this.setState({ modalOpen: true });
  };

  handleModalClose = () => {
    this.setState({ modalOpen: false });
  };

  handleActionMenuOpen = ev => {
    this.setState({ anchorEl: ev.currentTarget });
  };

  handleActionMenuClose = () => {
    this.setState({ anchorEl: null });
  };

  handleRemoveFromList = () => {
    this.props.ListUpdate({
      thingId: parseInt(this.props.item.id.toString()),
      addToLists: [],
      removeFromLists: [this.props.listContext!.id.toString()],
    });
  };

  navigateToDetail = (thing: Thing) => {
    // this.props.
  };

  renderPoster = (thing: Thing) => {
    let poster = getPosterPath(thing);

    const makeLink = (children: ReactNode, className?: string) => (
      <Link
        className={className}
        component={props => (
          <RouterLink {...props} to={'/item/' + thing.type + '/' + thing.id} />
        )}
      >
        {children}
      </Link>
    );

    if (poster) {
      return makeLink(
        <CardMedia
          className={this.props.classes.cardMedia}
          image={'https://image.tmdb.org/t/p/w300' + poster}
          title={thing.name}
        />,
      );
    } else {
      return makeLink(
        <div style={{ display: 'flex', width: '100%' }}>
          <Icon
            className={this.props.classes.missingMediaIcon}
            fontSize="inherit"
          >
            broken_image
          </Icon>
        </div>,
        this.props.classes.missingMedia,
      );
    }
  };

  renderActionMenu() {
    let { anchorEl } = this.state;

    return this.props.withActionButton && this.props.listContext ? (
      <React.Fragment>
        <IconButton onClick={this.handleActionMenuOpen}>
          <Icon>more_vert</Icon>
        </IconButton>
        <Menu
          anchorEl={anchorEl}
          open={Boolean(anchorEl)}
          onClose={this.handleActionMenuClose}
          disableAutoFocusItem
        >
          <MenuItem onClick={this.handleRemoveFromList}>Remove</MenuItem>
        </Menu>
      </React.Fragment>
    ) : null;
  }

  render() {
    let { item, classes, addButton, itemCardVisible } = this.props;

    return (
      <React.Fragment>
        <Grid key={item.id} sm={6} md={4} lg={3} item>
          <Card className={classes.card}>
            {this.renderPoster(item)}
           { itemCardVisible &&
            <CardContent className={classes.cardContent}>
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  margin: '-8px -8px 0 0',
                }}
              >
                <Typography
                  className={classes.title}
                  variant="h5"
                  component="h2"
                  title={item.name}
                >
                  {item.name}
                </Typography>
                {this.renderActionMenu()}
              </div>
              <Typography style={{ height: '60px' }}>
                <Truncate lines={3} ellipsis={<span>...</span>}>
                  {getDescription(item)}
                </Truncate>
              </Typography>
              {addButton ? (
                <Button
                  variant="contained"
                  color="primary"
                  className={classes.button}
                  onClick={() => this.handleModalOpen(item)}
                >
                  <Icon>playlist_add</Icon>
                  <Typography color="inherit">Add to List</Typography>
                </Button>
              ) : null}
            </CardContent>
           }
          </Card>
        </Grid>
        {addButton ? (
          <AddToListDialog
            open={this.state.modalOpen}
            userSelf={this.props.userSelf!}
            item={item}
          />
        ) : null}
      </React.Fragment>
    );
  }
}

const mapDispatchToProps = (dispatch: Dispatch) =>
  bindActionCreators(
    {
      ListUpdate,
    },
    dispatch,
  );

export default withStyles(styles)(
  connect(
    null,
    mapDispatchToProps,
  )(ItemCard),
);
