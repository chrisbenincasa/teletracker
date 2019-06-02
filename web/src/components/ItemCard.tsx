import {
  Button,
  Card,
  CardContent,
  CardMedia,
  createStyles,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  Grid,
  Icon,
  Link,
  Theme,
  Typography,
  WithStyles,
  withStyles,
  IconButton,
  Menu,
  MenuItem,
  Tooltip,
} from '@material-ui/core';
import classNames from 'classnames';
import * as R from 'ramda';
import { Link as RouterLink, withRouter } from 'react-router-dom';
import React, { Component, ReactNode } from 'react';
import Truncate from 'react-truncate';
import AddToListDialog from './AddToListDialog';
import { User, List, Thing, ActionType } from '../types';
import { getDescription, getPosterPath } from '../utils/metadata-access';
import { Dispatch, bindActionCreators } from 'redux';
import { ListUpdate, ListUpdatedInitiatedPayload } from '../actions/lists';
import { connect } from 'react-redux';
import { GridProps } from '@material-ui/core/Grid';
import DeleteIcon from '@material-ui/icons/Delete';
import Check from '@material-ui/icons/Check';
import { green, red } from '@material-ui/core/colors';
import withUser, { WithUserProps } from '../components/withUser';
import { AppState } from '../reducers';

import {
  updateUserItemTags,
  removeUserItemTags,
  UserUpdateItemTagsPayload,
} from '../actions/user';

const styles = (theme: Theme) =>
  createStyles({
    button: {
      width: '100%',
      marginTop: '0.35em',
    },
    description: {
      marginBottom: '0.35em',
    },
    title: {
      flex: 1,
      whiteSpace: 'nowrap',
      overflow: 'hidden',
      textOverflow: 'ellipsis',
    },
    card: {
      height: '100%',
      display: 'flex',
      flexDirection: 'column',
      position: 'relative',
    },
    cardMedia: {
      height: 0,
      width: '100%',
      paddingTop: '150%',
    },
    cardContent: {
      flexGrow: 1,
    },
    cardHover: {
      transition: 'opacity 370ms cubic-bezier(0.4,0,0.2,1)',
      backgroundColor: '#000',
      overflow: 'hidden',
      width: '100%',
      opacity: 0.7,
      display: 'block',
      zIndex: 1,
    },
    cardHoverExit: {
      opacity: 1,
    },
    hoverDelete: {
      position: 'absolute',
      top: 0,
      right: 0,
      color: '#fff',
      zIndex: 1,
      '&:hover': {
        color: red[900],
      },
    },
    hoverWatch: {
      color: '#fff',
      '&:hover': {
        color: green[900],
      },
    },
    missingMedia: {
      height: '150%',
      color: theme.palette.grey[500],
      display: 'flex',
      backgroundColor: theme.palette.grey[300],
      fontSize: '10em',
    },
    missingMediaIcon: {
      alignSelf: 'center',
      margin: '0 auto',
      display: 'inline-block',
    },
  });

interface ItemCardProps extends WithStyles<typeof styles> {
  key: string | number;
  item: Thing;
  userSelf?: User;

  // display props
  addButton?: boolean;
  itemCardVisible?: boolean;
  // If defined, we're viewing this item within the context of _this_ list
  // This is probably not scalable, but it'll work for now.
  listContext?: List;
  hoverDelete?: boolean;
  hoverWatch?: boolean;
  withActionButton: boolean;

  gridProps?: GridProps;
}

interface DispatchProps {
  ListUpdate: (payload: ListUpdatedInitiatedPayload) => void;
  updateUserItemTags: (payload: UserUpdateItemTagsPayload) => void;
  removeUserItemTags: (payload: UserUpdateItemTagsPayload) => void;
}

interface ItemCardState {
  modalOpen: boolean;

  // åction button menu
  anchorEl: any;
  hover: boolean;
  deleteConfirmationOpen: boolean;
  deleted: boolean;
  currentId: number;
  currentType: string;
}

type Props = ItemCardProps & DispatchProps & WithUserProps;

class ItemCard extends Component<Props, ItemCardState> {
  static defaultProps = {
    withActionButton: false,
    itemCardVisible: true,
    hoverDelete: false,
    hoverWatch: true,
  };

  state: ItemCardState = {
    modalOpen: false,
    anchorEl: null,
    hover: false,
    deleteConfirmationOpen: false,
    deleted: false,
    currentId: 0,
    currentType: '',
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

  componentDidMount() {
    let { item } = this.props;
    let itemId = Number(item.id);

    this.setState({
      currentId: itemId,
    });

    // this.props.fetchItemDetails(itemId, itemType);
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

  handleHoverEnter = () => {
    this.setState({ hover: true });
  };

  handleHoverExit = () => {
    this.setState({ hover: false });
  };

  handleDeleteModalOpen = () => {
    this.setState({ deleteConfirmationOpen: true });
  };

  handleDeleteModalClose = () => {
    this.setState({ deleteConfirmationOpen: false });
  };

  handleRemoveFromList = () => {
    this.props.ListUpdate({
      thingId: parseInt(this.props.item.id.toString()),
      addToLists: [],
      removeFromLists: [this.props.listContext!.id.toString()],
    });
    this.setState({ deleted: true });
    this.handleDeleteModalClose();
  };

  toggleItemWatched = () => {
    let payload = {
      thingId: this.state.currentId,
      action: ActionType.Watched,
    };

    if (this.itemMarkedAsWatched()) {
      this.props.removeUserItemTags(payload);
    } else {
      this.props.updateUserItemTags(payload);
    }
  };

  itemMarkedAsWatched = () => {
    console.log(this.props);
    if (this.props.item && this.props.item.userMetadata) {
      console.log('test');
      return R.any(tag => {
        return tag.action == ActionType.Watched;
      }, this.props.item.userMetadata.tags);
    }

    return false;
  };

  renderPoster = (thing: Thing) => {
    let { classes, hoverDelete, hoverWatch } = this.props;
    let { deleted, hover } = this.state;
    let poster = getPosterPath(thing);

    const makeLink = (children: ReactNode, className?: string) => (
      <React.Fragment>
        <div className={hover ? classes.cardHover : classes.cardHoverExit}>
          {hoverDelete && hover && (
            <Tooltip
              title={'Remove from List'}
              placement="top"
              style={{ position: 'absolute', top: 0, left: 0, zIndex: 1 }}
            >
              <IconButton
                aria-label="Delete"
                className={classes.hoverDelete}
                onClick={this.handleDeleteModalOpen}
              >
                <DeleteIcon />
              </IconButton>
            </Tooltip>
          )}
          {hoverWatch && hover && this.renderWatchedToggle()}
          <Link
            className={className}
            component={props => (
              <RouterLink
                {...props}
                to={'/item/' + thing.type + '/' + thing.id}
              />
            )}
            style={{ position: 'relative' }}
          >
            {children}
          </Link>
        </div>
      </React.Fragment>
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

  renderDialog() {
    let { deleteConfirmationOpen } = this.state;

    return (
      <div>
        <Dialog
          open={deleteConfirmationOpen}
          onClose={this.handleDeleteModalClose}
          aria-labelledby="alert-dialog-title"
          aria-describedby="alert-dialog-description"
        >
          <DialogTitle id="alert-dialog-title">
            {'Remove from List'}
          </DialogTitle>
          <DialogContent>
            <DialogContentText id="alert-dialog-description">
              Are you sure you want to remove this from your list?
            </DialogContentText>
          </DialogContent>
          <DialogActions>
            <Button onClick={this.handleDeleteModalClose} color="primary">
              Cancel
            </Button>
            <Button
              onClick={this.handleRemoveFromList}
              color="primary"
              autoFocus
            >
              Remove
            </Button>
          </DialogActions>
        </Dialog>
      </div>
    );
  }

  renderWatchedToggle = () => {
    let { classes } = this.props;
    return (
      <Tooltip
        title={
          this.itemMarkedAsWatched() ? 'Mark as not watched' : 'Mark as watched'
        }
        placement="top"
        style={{ position: 'absolute', top: 0, right: 0, zIndex: 1 }}
      >
        <IconButton aria-label="Delete" onClick={this.toggleItemWatched}>
          <Check className={classes.hoverWatch} />
        </IconButton>
      </Tooltip>
    );
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
    let { deleted, hover } = this.state;

    let gridProps: Partial<GridProps> = {
      item: true,
      sm: 6,
      md: 4,
      lg: 3,
      ...this.props.gridProps,
    };

    return (
      <React.Fragment>
        <Grid key={!deleted ? item.id : 'deleted'} {...gridProps}>
          <Card
            className={classes.card}
            onMouseEnter={this.handleHoverEnter}
            onMouseLeave={this.handleHoverExit}
          >
            {this.renderPoster(item)}
            {itemCardVisible && (
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
            )}
          </Card>
        </Grid>
        {addButton ? (
          <AddToListDialog
            open={this.state.modalOpen}
            userSelf={this.props.userSelf!}
            item={item}
          />
        ) : null}
        {this.renderDialog()}
      </React.Fragment>
    );
  }
}

const mapDispatchToProps = (dispatch: Dispatch) =>
  bindActionCreators(
    {
      ListUpdate,
      updateUserItemTags,
      removeUserItemTags,
    },
    dispatch,
  );

export default withUser(
  withStyles(styles)(
    connect(
      null,
      mapDispatchToProps,
    )(ItemCard),
  ),
);
